/**
 * Copyright (c) 2005, KoLmafia development team
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
 *  [3] Neither the name "KoLmafia development team" nor the names of
 *      its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;

// layout
import java.awt.Dimension;
import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.awt.GridLayout;

// containers
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JTabbedPane;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;

// utilities
import java.util.Properties;
import java.util.StringTokenizer;
import net.java.dev.spellcast.utilities.LockableListModel;

/**
 * <p>Handles all of the customizable user options in <code>KoLmafia</code>.
 * This class presents all of the options that the user can customize
 * in their adventuring and uses the appropriate <code>KoLSettings</code>
 * in order to display them.  This class also uses <code>KoLSettings</code>
 * to record the user's preferences for upcoming sessions.</p>
 *
 * <p>If this class is accessed before login, it will modify global settings
 * ONLY, and if the character already has settings, any modification of
 * global settings will not modify their own.  Accessing this class after
 * login will result in modification of the character's own settings ONLY,
 * and will not modify any global settings.</p>
 *
 * <p>Proxy settings are a special exception to this rule - because the
 * Java Virtual Machine requires the proxy settings to be specified at
 * a global level, though the settings are changed appropriately on disk,
 * only the most recently loaded settings will be active on the current
 * instance of the JVM.  If separate characters need separate proxies,
 * they cannot be run in the same JVM instance.</p>
 */

public class OptionsFrame extends KoLFrame
{
	/**
	 * Constructs a new <code>OptionsFrame</code> that will be
	 * associated with the given client.  When this frame is
	 * closed, it will attempt to return focus to the currently
	 * active frame; note that if this is done while the client
	 * is shuffling active frames, closing the window will not
	 * properly transfer focus.
	 *
	 * @param	client	The client to be associated with this <code>OptionsFrame</code>
	 */

	public OptionsFrame( KoLmafia client )
	{
		super( "KoLmafia: " + ((client == null) ? "UI Test" :
			(client.getLoginName() == null) ? "Global" : client.getLoginName()) + " Preferences", client );
		setResizable( false );

		getContentPane().setLayout( new CardLayout( 10, 10 ) );

		JTabbedPane tabs = new JTabbedPane();

		// Because none of the frames support setStatusMessage,
		// the content panel is arbitrary

		this.client = client;
		contentPanel = null;

		tabs.addTab( "Login", new LoginOptionsPanel() );
		tabs.addTab( "Startup", new StartupOptionsPanel() );
		tabs.addTab( "Battle", new BattleOptionsPanel() );
		tabs.addTab( "Mall", new ResultsOptionsPanel() );
		tabs.addTab( "Sewer", new SewerOptionsPanel() );
		tabs.addTab( "Chat", new ChatOptionsPanel() );

		getContentPane().add( tabs, BorderLayout.CENTER );
		addWindowListener( new ReturnFocusAdapter() );

		addMenuBar();
	}

	/**
	 * Utility method used to add a menu bar to the <code>LoginFrame</code>.
	 * The menu bar contains configuration options and the general license
	 * information associated with <code>KoLmafia</code>.
	 */

	private void addMenuBar()
	{
		JMenuBar menuBar = new JMenuBar();
		this.setJMenuBar( menuBar );

		addConfigureMenu( menuBar );
		addHelpMenu( menuBar );
	}

	/**
	 * This panel handles all of the things related to login
	 * options, including which server to use for login and
	 * all other requests, as well as the user's proxy settings
	 * (if applicable).
	 */

	private class LoginOptionsPanel extends OptionsPanel
	{
		private JComboBox serverSelect;
		private JTextField proxyHost;
		private JTextField proxyPort;
		private JTextField proxyLogin;
		private JTextField proxyPassword;

		/**
		 * Constructs a new <code>LoginOptionsPanel</code>, containing a
		 * place for the users to select their desired server and for them
		 * to modify any applicable proxy settings.
		 */

		public LoginOptionsPanel()
		{
			super( new Dimension( 120, 20 ), new Dimension( 165, 20 ) );

			LockableListModel servers = new LockableListModel();
			servers.add( "(Auto Detect)" );
			servers.add( "Use Login Server 1" );
			servers.add( "Use Login Server 2" );
			servers.add( "Use Login Server 3" );

			serverSelect = new JComboBox( servers );
			proxyHost = new JTextField();
			proxyPort = new JTextField();
			proxyLogin = new JTextField();
			proxyPassword = new JPasswordField();

			VerifiableElement [] elements = new VerifiableElement[5];
			elements[0] = new VerifiableElement( "KoL Server: ", serverSelect );
			elements[1] = new VerifiableElement( "Proxy Host: ", proxyHost );
			elements[2] = new VerifiableElement( "Proxy Port: ", proxyPort );
			elements[3] = new VerifiableElement( "Proxy Login: ", proxyLogin );
			elements[4] = new VerifiableElement( "Proxy Password: ", proxyPassword );

			setContent( elements, true );
		}

		public void clear()
		{	(new LoadDefaultSettingsThread()).run();
		}

		protected void actionConfirmed()
		{	(new StoreSettingsThread()).start();
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to load the default settings.
		 */

		private class LoadDefaultSettingsThread extends OptionsThread
		{
			public void run()
			{
				if ( client == null )
				{
					System.setProperty( "loginServer", "0" );
					System.setProperty( "proxySet", "false" );
				}

				serverSelect.setSelectedIndex( Integer.parseInt( settings.getProperty( "loginServer" ) ) );

				if ( settings.getProperty( "proxySet" ).equals( "true" ) )
				{
					proxyHost.setText( settings.getProperty( "http.proxyHost" ) );
					proxyPort.setText( settings.getProperty( "http.proxyPort" ) );
					proxyLogin.setText( settings.getProperty( "http.proxyUser" ) );
					proxyPassword.setText( settings.getProperty( "http.proxyPassword" ) );
				}
				else
				{
					proxyHost.setText( "" );
					proxyPort.setText( "" );
					proxyLogin.setText( "" );
					proxyPassword.setText( "" );
				}

				(new StatusMessageChanger( "" )).run();
			}
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to store the new settings.
		 */

		private class StoreSettingsThread extends OptionsThread
		{
			public void run()
			{
				if ( proxyHost.getText().trim().length() != 0 )
				{
					settings.setProperty( "proxySet", "true" );
					settings.setProperty( "http.proxyHost", proxyHost.getText() );
					settings.setProperty( "http.proxyPort", proxyPort.getText() );

					if ( proxyLogin.getText().trim().length() != 0 )
					{
						settings.setProperty( "http.proxyUser", proxyLogin.getText() );
						settings.setProperty( "http.proxyPassword", proxyPassword.getText() );
					}
					else
					{
						settings.remove( "http.proxyUser" );
						settings.remove( "http.proxyPassword" );
					}
				}
				else
				{
					settings.setProperty( "proxySet", "false" );
					settings.remove( "http.proxyHost" );
					settings.remove( "http.proxyPort" );
					settings.remove( "http.proxyUser" );
					settings.remove( "http.proxyPassword" );
				}

				// Next, change the server that's used to login;
				// find out the selected index.

				settings.setProperty( "loginServer", "" + serverSelect.getSelectedIndex() );

				// Save the settings that were just set; that way,
				// the next login can use them.

				saveSettings();
				KoLRequest.applySettings();
			}
		}
	}

	/**
	 * This panel allows the user to select which things they would
	 * like to do on startup.  Some people only use this for doing
	 * small things, not full-blown character management.  This
	 * screen allows users to customize their login sequence.
	 */

	private class StartupOptionsPanel extends OptionsPanel
	{
		private JCheckBox [] optionBoxes;
		private final String [] optionKeys = { "skipCharacterData", "skipInventory", "skipFamiliarData" };
		private final String [] optionNames = { "Skip character data retrieval", "Skip inventory retrieval", "Skip familiar data retrieval" };

		/**
		 * Constructs a new <code>SewerOptionsPanel</code> containing an
		 * alphabetized list of items available through the lucky sewer
		 * adventure.
		 */

		public StartupOptionsPanel()
		{
			super( new Dimension( 200, 20 ), new Dimension( 20, 20 ) );

			optionBoxes = new JCheckBox[ optionNames.length ];
			for ( int i = 0; i < optionNames.length; ++i )
				optionBoxes[i] = new JCheckBox();

			VerifiableElement [] elements = new VerifiableElement[ optionNames.length ];
			for ( int i = 0; i < optionNames.length; ++i )
				elements[i] = new VerifiableElement( optionNames[i], JLabel.LEFT, optionBoxes[i] );

			setContent( elements, false );
		}

		public void clear()
		{	(new LoadDefaultSettingsThread()).start();
		}

		protected void actionConfirmed()
		{	(new StoreSettingsThread()).start();
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to load the default settings.
		 */

		private class LoadDefaultSettingsThread extends OptionsThread
		{
			public void run()
			{
				for ( int i = 0; i < optionKeys.length; ++i )
					optionBoxes[i].setSelected( settings.getProperty( optionKeys[i] ) != null );
				(new StatusMessageChanger( "" )).run();
			}
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to store the new settings.
		 */

		private class StoreSettingsThread extends OptionsThread
		{
			public void run()
			{
				if ( client != null )
				{
					for ( int i = 0; i < optionKeys.length; ++i )
					{
						if ( optionBoxes[i].isSelected() )
							settings.setProperty( optionKeys[i], "true" );
						else
							settings.remove( optionKeys[i] );
					}

					if ( settings instanceof KoLSettings )
						((KoLSettings)settings).saveSettings();
				}

				saveSettings();
			}
		}
	}

	/**
	 * This panel allows the user to select how they would like to fight
	 * their battles.  Everything from attacks, attack items, recovery items,
	 * retreat, and battle skill usage will be supported when this panel is
	 * finalized.  For now, however, it only customizes attacks.
	 */

	private class BattleOptionsPanel extends OptionsPanel
	{
		private LockableListModel actions;
		private LockableListModel actionNames;

		private JComboBox actionSelect;

		/**
		 * Constructs a new <code>BattleOptionsPanel</code> containing a
		 * way for the users to choose the way they want to fight battles
		 * encountered during adventuring.
		 */

		public BattleOptionsPanel()
		{
			super( new Dimension( 120, 20 ), new Dimension( 165, 20 ) );

			actions = new LockableListModel();
			actions.add( "attack" );
			actions.add( "moxman" );

			actionNames = new LockableListModel();
			actionNames.add( "Attack with Weapon" );
			actionNames.add( "Moxious Maneuver" );

			actionSelect = new JComboBox( actionNames );

			VerifiableElement [] elements = new VerifiableElement[1];
			elements[0] = new VerifiableElement( "Battle Style: ", actionSelect );

			setContent( elements );
		}

		public void clear()
		{	(new LoadDefaultSettingsThread()).start();
		}

		protected void actionConfirmed()
		{	(new StoreSettingsThread()).start();
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to load the default settings.
		 */

		private class LoadDefaultSettingsThread extends OptionsThread
		{
			public void run()
			{
				String battleSettings = settings.getProperty( "battleAction" );

				// If there are no default settings, simply skip the
				// attempt at loading them.

				actionNames.setSelectedIndex( battleSettings == null ? 0 : actions.indexOf( battleSettings ) );
				(new StatusMessageChanger( "" )).run();
			}
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to store the new settings.
		 */

		private class StoreSettingsThread extends OptionsThread
		{
			public void run()
			{
				settings.setProperty( "battleAction", (String) actions.get( actionNames.getSelectedIndex() ) );
				saveSettings();
			}
		}
	}

	/**
	 * This panel allows the user to select which item they would like
	 * to trade with the gnomes in the sewers of Seaside Town, in
	 * exchange for their ten-leaf clover.  These settings only apply
	 * to the Lucky Sewer adventure.
	 */

	private class SewerOptionsPanel extends OptionsPanel
	{
		private JCheckBox [] items;

		private final String [] itemnames = { "seal-clubbing club", "seal tooth", "helmet turtle",
			"scroll of turtle summoning", "pasta spoon", "ravioli hat", "saucepan", "spices", "disco mask",
			"disco ball", "stolen accordion", "mariachi pants", "worthless trinket" };

		/**
		 * Constructs a new <code>SewerOptionsPanel</code> containing an
		 * alphabetized list of items available through the lucky sewer
		 * adventure.
		 */

		public SewerOptionsPanel()
		{
			super( new Dimension( 200, 20 ), new Dimension( 20, 20 ) );

			items = new JCheckBox[ itemnames.length ];
			for ( int i = 0; i < items.length; ++i )
				items[i] = new JCheckBox();

			VerifiableElement [] elements = new VerifiableElement[ items.length ];
			for ( int i = 0; i < items.length; ++i )
				elements[i] = new VerifiableElement( itemnames[i], JLabel.LEFT, items[i] );

			java.util.Arrays.sort( elements );
			setContent( elements, false );
		}

		public void clear()
		{	(new LoadDefaultSettingsThread()).start();
		}

		protected void actionConfirmed()
		{	(new StoreSettingsThread()).start();
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to load the default settings.
		 */

		private class LoadDefaultSettingsThread extends OptionsThread
		{
			public void run()
			{
				String sewerSettings = (client == null) ? null :
					settings.getProperty( "luckySewer" );

				// If there are no default settings, simply skip the
				// attempt at loading them.

				if ( sewerSettings == null )
					return;

				// If there are default settings, make sure that the
				// appropriate check box is checked.

				StringTokenizer st = new StringTokenizer( sewerSettings, "," );
				for ( int i = 0; i < items.length; ++i )
					items[i].setSelected( false );

				while ( st.hasMoreTokens() )
					items[ Integer.parseInt( st.nextToken() ) - 1 ].setSelected( true );

				(new StatusMessageChanger( "" )).run();
			}
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to store the new settings.
		 */

		private class StoreSettingsThread extends OptionsThread
		{
			public void run()
			{
				int [] selected = new int[3];
				int selectedCount = 0;

				for ( int i = 0; i < items.length; ++i )
				{
					if ( items[i].isSelected() )
					{
						if ( selectedCount < 3 )
							selected[selectedCount] = i + 1;
						++selectedCount;
					}
				}

				if ( selectedCount != 3 )
				{
					(new StatusMessageChanger( "You did not select exactly three items." )).run();
					return;
				}

				if ( client != null )
				{
					settings.setProperty( "luckySewer", selected[0] + "," + selected[1] + "," + selected[2] );
					if ( settings instanceof KoLSettings )
						((KoLSettings)settings).saveSettings();
				}

				saveSettings();
			}
		}
	}

	/**
	 * Panel used for handling chat-related options and preferences,
	 * including font size, window management and maybe, eventually,
	 * coloring options for contacts.
	 */

	private class ChatOptionsPanel extends OptionsPanel
	{
		private JComboBox fontSizeSelect;
		private JComboBox chatStyleSelect;

		public ChatOptionsPanel()
		{
			super( new Dimension( 120, 20 ), new Dimension( 165, 20 ) );

			LockableListModel fontSizes = new LockableListModel();
			for ( int i = 1; i <= 7; ++i )
				fontSizes.add( new Integer( i ) );
			fontSizeSelect = new JComboBox( fontSizes );

			LockableListModel chatStyles = new LockableListModel();
			chatStyles.add( "Messenger style" );
			chatStyles.add( "Trivia hosting style" );
			chatStyleSelect = new JComboBox( chatStyles );

			VerifiableElement [] elements = new VerifiableElement[2];
			elements[0] = new VerifiableElement( "Font Size: ", fontSizeSelect );
			elements[1] = new VerifiableElement( "Chat Style: ", chatStyleSelect );

			setContent( elements );
		}

		public void clear()
		{	(new LoadDefaultSettingsThread()).start();
		}

		protected void actionConfirmed()
		{	(new StoreSettingsThread()).start();
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to load the default settings.
		 */

		private class LoadDefaultSettingsThread extends OptionsThread
		{
			public void run()
			{
				// Begin by loading the font size from the user
				// settings - for backwards compatibility, this
				// may not exist yet.

				String fontSize = settings.getProperty( "fontSize" );

				if ( fontSize != null )
				{
					fontSizeSelect.setSelectedItem( Integer.valueOf( fontSize ) );
					LimitedSizeChatBuffer.setFontSize( Integer.parseInt( fontSize ) );
				}
				else
					fontSizeSelect.setSelectedItem( new Integer( 3 ) );

				// Next, load the kind of chat style the user
				// is using - again, for backwards compatibility,
				// this may not exist yet.

				String chatStyle = settings.getProperty( "chatStyle" );
				chatStyleSelect.setSelectedIndex( (chatStyle != null) ? Integer.parseInt( chatStyle ) : 0 );
			}
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to store the new settings.
		 */

		private class StoreSettingsThread extends OptionsThread
		{
			public void run()
			{
				Integer fontSize = (Integer) fontSizeSelect.getSelectedItem();
				settings.setProperty( "fontSize", fontSize.toString() );
				LimitedSizeChatBuffer.setFontSize( fontSize.intValue() );
				settings.setProperty( "chatStyle", "" + chatStyleSelect.getSelectedIndex() );
				saveSettings();
			}
		}
	}

	/**
	 * An internal class used for handling mall options.  This includes
	 * default mall limiting, mall sorting and sending items to the mall.
	 */

	private class ResultsOptionsPanel extends OptionsPanel
	{
		private JTextField defaultLimitField;
		private JComboBox forceSortSelect;
		private JComboBox promptForPriceSelect;
		private JComboBox useClosetForCreationSelect;
		private JComboBox autoRepairBoxesSelect;

		public ResultsOptionsPanel()
		{
			super( new Dimension( 120, 20 ), new Dimension( 165, 20 ) );

			defaultLimitField = new JTextField( "13" );

			LockableListModel forceSorting = new LockableListModel();
			forceSorting.add( "No Sorting" );
			forceSorting.add( "Force Price Sort" );

			forceSortSelect = new JComboBox( forceSorting );

			LockableListModel promptForPrices = new LockableListModel();
			promptForPrices.add( "Prompt for Price" );
			promptForPrices.add( "Instant Billionaire" );

			promptForPriceSelect = new JComboBox( promptForPrices );

			LockableListModel useClosetForCreation = new LockableListModel();
			useClosetForCreation.add( "Inventory only" );
			useClosetForCreation.add( "Closet and inventory" );

			useClosetForCreationSelect = new JComboBox( useClosetForCreation );

			LockableListModel autoRepairBoxes = new LockableListModel();
			autoRepairBoxes.add( "Halt on explosion" );
			autoRepairBoxes.add( "Auto-repair on explosion" );

			autoRepairBoxesSelect = new JComboBox( autoRepairBoxes );

			VerifiableElement [] elements = new VerifiableElement[5];
			elements[0] = new VerifiableElement( "Default Limit: ", defaultLimitField );
			elements[1] = new VerifiableElement( "Sorting Style: ", forceSortSelect );
			elements[2] = new VerifiableElement( "Automall Style: ", promptForPriceSelect );
			elements[3] = new VerifiableElement( "Ingredient Source: ", useClosetForCreationSelect );
			elements[4] = new VerifiableElement( "Auto-Repair: ", autoRepairBoxesSelect );

			setContent( elements );
		}

		public void clear()
		{	(new LoadDefaultSettingsThread()).start();
		}

		protected void actionConfirmed()
		{	(new StoreSettingsThread()).start();
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to load the default settings.
		 */

		private class LoadDefaultSettingsThread extends OptionsThread
		{
			public void run()
			{
				String defaultLimitSetting = settings.getProperty( "defaultLimit" );
				String forceSortSetting = settings.getProperty( "forceSorting" );
				String promptForPriceSetting = settings.getProperty( "promptForPrice" );
				String useClosetForCreationSetting = settings.getProperty( "useClosetForCreation" );
				String autoRepairBoxesSetting = settings.getProperty( "autoRepairBoxes" );

				// If there are no default settings, simply skip the
				// attempt at loading them.

				defaultLimitField.setText( defaultLimitSetting == null ? "13" : defaultLimitSetting );

				if ( forceSortSetting == null || forceSortSetting.equals( "false" ) )
					forceSortSelect.setSelectedIndex( 0 );
				else
					forceSortSelect.setSelectedIndex( 1 );

				if ( promptForPriceSetting == null || promptForPriceSetting.equals( "true" ) )
					promptForPriceSelect.setSelectedIndex( 0 );
				else
					promptForPriceSelect.setSelectedIndex( 1 );

				if ( useClosetForCreationSetting == null || useClosetForCreationSetting.equals( "false" ) )
					useClosetForCreationSelect.setSelectedIndex( 0 );
				else
					useClosetForCreationSelect.setSelectedIndex( 1 );

				if ( autoRepairBoxesSetting == null || autoRepairBoxesSetting.equals( "false" ) )
					autoRepairBoxesSelect.setSelectedIndex( 0 );
				else
					autoRepairBoxesSelect.setSelectedIndex( 1 );

				(new StatusMessageChanger( "" )).run();
			}
		}

		/**
		 * In order to keep the user interface from freezing (or at
		 * least appearing to freeze), this internal class is used
		 * to store the new settings.
		 */

		private class StoreSettingsThread extends OptionsThread
		{
			public void run()
			{
				settings.setProperty( "defaultLimit", defaultLimitField.getText().length() == 0 ? "13" : defaultLimitField.getText() );
				settings.setProperty( "forceSorting", "" + (forceSortSelect.getSelectedIndex() == 1) );
				settings.setProperty( "promptForPrice", "" + (promptForPriceSelect.getSelectedIndex() == 0) );
				settings.setProperty( "useClosetForCreation", "" + (useClosetForCreationSelect.getSelectedIndex() == 1) );
				settings.setProperty( "autoRepairBoxes", "" + (autoRepairBoxesSelect.getSelectedIndex() == 1) );
				saveSettings();
			}
		}
	}

	/**
	 * A generic panel which adds a label to the bottom of the KoLPanel
	 * to update the panel's status.  It also provides a thread which is
	 * guaranteed to be a daemon thread for updating the frame which
	 * also retrieves a reference to the client's current settings.
	 */

	private abstract class OptionsPanel extends LabeledKoLPanel
	{
		protected Properties settings;

		public OptionsPanel( Dimension left, Dimension right )
		{	this( null, left, right );
		}

		public OptionsPanel( String panelTitle, Dimension left, Dimension right )
		{
			super( panelTitle, left, right );
			settings = (client == null) ? System.getProperties() : client.getSettings();
		}

		protected void saveSettings()
		{
			if ( settings instanceof KoLSettings )
				((KoLSettings)settings).saveSettings();
			(new StatusMessageChanger( "Settings saved." )).run();

			try { Thread.sleep( 5000 ); }
			catch ( Exception e ) {}

			(new StatusMessageChanger( "" )).run();
		}

		protected abstract class OptionsThread extends Thread
		{
			public OptionsThread()
			{	setDaemon( true );
			}
		}
	}

	/**
	 * The main method used in the event of testing the way the
	 * user interface looks.  This allows the UI to be tested
	 * without having to constantly log in and out of KoL.
	 */

	public static void main( String [] args )
	{
		KoLFrame uitest = new OptionsFrame( null );
		uitest.pack();  uitest.setVisible( true );  uitest.requestFocus();
	}
}