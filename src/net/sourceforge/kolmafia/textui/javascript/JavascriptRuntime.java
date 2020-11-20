/**
 * Copyright (c) 2005-2020, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION ) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE ) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia.textui.javascript;

import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.request.RelayRequest;
import net.sourceforge.kolmafia.textui.parsetree.ProxyRecordValue;
import net.sourceforge.kolmafia.textui.parsetree.Type;
import net.sourceforge.kolmafia.textui.parsetree.Value;
import net.sourceforge.kolmafia.textui.parsetree.VariableReference;
import net.sourceforge.kolmafia.textui.DataTypes;
import net.sourceforge.kolmafia.textui.ScriptRuntime;
import net.sourceforge.kolmafia.textui.RuntimeLibrary;
import net.sourceforge.kolmafia.textui.ScriptException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class JavascriptRuntime
	implements ScriptRuntime
{
	static Map<Thread, JavascriptRuntime> runningRuntimes = new ConcurrentHashMap<>();

	private File scriptFile = null;
	private String scriptString = null;

	private State runtimeState = State.EXIT;

	// For relay scripts.
	private RelayRequest relayRequest = null;
	private StringBuffer serverReplyBuffer = null;

	private LinkedHashMap<String, LinkedHashMap<String, StringBuilder>> batched;

	public static String toCamelCase( String name )
	{
		if ( name == null )
		{
			return null;
		}

		boolean first = true;
		StringBuilder result = new StringBuilder();
		for ( String word : name.split( "_" ) )
		{
			if ( first )
			{
				result.append( word.charAt( 0 ) );
				first = false;
			}
			else
			{
				result.append( Character.toUpperCase( word.charAt( 0 ) ) );
			}
			result.append( word.substring( 1 ) );
		}

		return result.toString();
	}

	public static String capitalize( String name )
	{
		if ( name == null )
		{
			return null;
		}

		StringBuilder result = new StringBuilder();
		result.append( Character.toUpperCase( name.charAt( 0 ) ) );
		result.append( name.substring( 1 ) );
		return result.toString();
	}

	public JavascriptRuntime( File scriptFile )
	{
		this.scriptFile = scriptFile;
	}

	public JavascriptRuntime( String scriptString )
	{
		this.scriptString = scriptString;
	}

	private void initRuntimeLibrary( Context cx, Scriptable scope )
	{
		Scriptable stdLib = cx.newObject(scope);
		Set<String> functionNameSet = new TreeSet<>();
		for ( net.sourceforge.kolmafia.textui.parsetree.Function libraryFunction : RuntimeLibrary.functions )
		{
			// Blacklist a number of types.
			List<Type> allTypes = new ArrayList<>();
			allTypes.add( libraryFunction.getType() );
			for ( VariableReference variableReference : libraryFunction.getVariableReferences() )
			{
				allTypes.add( variableReference.getType() );
			}
			if ( allTypes.contains( DataTypes.MATCHER_TYPE ) ) continue;

			functionNameSet.add(libraryFunction.getName());
		}
		for ( String libraryFunctionName : functionNameSet )
		{
			ScriptableObject.putProperty( stdLib, toCamelCase( libraryFunctionName ),
				new JavascriptAshStub( this, libraryFunctionName ) );
		}
		ScriptableObject.putProperty( scope, "Lib", stdLib );
	}

	private static void initEnumeratedType( Context cx, Scriptable scope, Class<?> recordValueClass, Type valueType )
	{
		EnumeratedWrapperPrototype prototype = new EnumeratedWrapperPrototype( recordValueClass, valueType );
		prototype.initToScope( cx, scope );
	}

	private static void initEnumeratedTypes( Context cx, Scriptable scope )
	{
		for ( Type valueType : DataTypes.enumeratedTypes )
		{
			String typeName = capitalize( valueType.getName() );
			Class<?> proxyRecordValueClass = Value.class;
			for ( Class<?> testProxyRecordValueClass : ProxyRecordValue.class.getDeclaredClasses() )
			{
				if ( testProxyRecordValueClass.getSimpleName().equals( typeName + "Proxy" ) )
				{
					proxyRecordValueClass = testProxyRecordValueClass;
				}
			}

			initEnumeratedType( cx, scope, proxyRecordValueClass, valueType );
		}
	}

	public Value execute( String[] arguments )
	{
		// TODO: Support for requesting user arguments if missing.
		Context cx = Context.enter();
		cx.setLanguageVersion( Context.VERSION_ES6 );
		runningRuntimes.put( Thread.currentThread(), this );

		if ( arguments == null )
		{
			arguments = new String[] {};
		}

		try
		{
			Scriptable scope = cx.initSafeStandardObjects();

			initRuntimeLibrary(cx, scope);
			initEnumeratedTypes(cx, scope);

			setState(State.NORMAL);

			Object returnValue = null;

			try
			{
				if ( scriptFile != null )
				{
					FileReader scriptFileReader = null;
					try
					{
						scriptFileReader = new FileReader( scriptFile );
						returnValue = cx.evaluateReader( scope, scriptFileReader, scriptFile.getName(), 0, null );
					}
					catch ( FileNotFoundException e )
					{
						KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, "File not found." );
					}
					catch ( IOException e )
					{
						KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR,
							"JavaScript file I/O error: " + e.getMessage() );
					}
					finally
					{
						if ( scriptFileReader != null )
						{
							try
							{
								scriptFileReader.close();
							}
							catch ( IOException e )
							{
								KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR,
									"JavaScript file I/O error: " + e.getMessage() );
							}
						}
					}
				}
				else
				{
					returnValue = cx.evaluateString( scope, scriptString, "command line", 1, null );
				}
				Object mainFunction = scope.get( "main", scope );
				if ( mainFunction instanceof Function )
				{
					returnValue = ( (Function) mainFunction ).call( cx, scope, cx.newObject(scope), arguments );
				}
			}
			catch ( ScriptInterruptException e )
			{
				// This only happens on world peace. Fall through and exit the interpreter.
			}
			catch ( WrappedException e )
			{
				Throwable unwrapped = e.getWrappedException();
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR,
					unwrapped.getMessage() + "\n" + e.getScriptStackTrace() );
			}
			catch ( EvaluatorException e )
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR,
					"JavaScript evaluator exception: " + e.getMessage() + "\n" + e.getScriptStackTrace() );
			}
			catch ( EcmaError e )
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR,
					"JavaScript error: " + e.getErrorMessage() + "\n" + e.getScriptStackTrace() ); 
			}
			catch ( JavaScriptException e )
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR,
					"JavaScript exception: " + e.getMessage() + "\n" + e.getScriptStackTrace() );
			}
			catch ( ScriptException e )
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR,
					"Script exception: " + e.getMessage() );
			}
			finally
			{
				setState(State.EXIT);
			}

			return new ValueConverter( cx, scope ).fromJava( returnValue );
		}
		finally
		{
			runningRuntimes.remove( Thread.currentThread() );
			EnumeratedWrapperPrototype.cleanup( cx );
			Context.exit();
		}
	}

	public static void interruptAll()
	{
		for ( Thread thread : runningRuntimes.keySet() )
		{
			thread.interrupt();
		}
	}

	@Override
	public ScriptException runtimeException( final String message )
	{
		return new ScriptException( Context.reportRuntimeError( message ).getMessage() );
	}

	@Override
	public ScriptException runtimeException2( final String message1, final String message2 )
	{
		return new ScriptException( Context.reportRuntimeError( message1 + " " + message2 ).getMessage() );
	}

	@Override
	public RelayRequest getRelayRequest()
	{
		return relayRequest;
	}

	@Override
	public StringBuffer getServerReplyBuffer()
	{
		return serverReplyBuffer;
	}

	@Override
	public State getState()
	{
		return runtimeState;
	}

	@Override
	public void setState(final State newState)
	{
		runtimeState = newState;
	}

	@Override
	public LinkedHashMap<String, LinkedHashMap<String, StringBuilder>> getBatched()
	{
		return batched;
	}

	@Override
	public void setBatched( LinkedHashMap<String, LinkedHashMap<String, StringBuilder>> batched )
	{
		this.batched = batched;
	}
}