//chliu created 2013/4/18 15:17:01
package yydict;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;

import net.rim.device.api.io.LineReader;
import net.rim.device.api.system.Application;
import net.rim.device.api.system.Characters;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Manager;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.BasicEditField;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.EditField;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.container.VerticalFieldManager;
//import net.rim.device.api.ui.picker.FilePicker;



class WordItem 
{
    //public String word; 
    public int offset; 
    public int audioSize; 
    public int defSize; 

    WordItem (int offset2, int audioSize2, int defSize2 )
    {
        offset = offset2;
        audioSize = audioSize2;
        defSize = defSize2;
    }
};

class TextBoxField extends Manager 
{
    private static final int MAX_TEXT_NUM = 16*1024;
	int managerWidth = 0;
    int managerHeight = 0; 

    private VerticalFieldManager vfm = new VerticalFieldManager(VERTICAL_SCROLL | 
    		VERTICAL_SCROLLBAR | USE_ALL_WIDTH | USE_ALL_HEIGHT);
    private EditField editField = new EditField("", "", MAX_TEXT_NUM, FOCUSABLE);

    TextBoxField(int width, int height, long style) 
    {
        super(style | NO_VERTICAL_SCROLL | NO_HORIZONTAL_SCROLL);

        managerWidth = width;
        managerHeight = height;
            
        add(vfm);
        vfm.add(editField);
     } 
		
    public String getText() 
    {
	    return editField.getText();
    }
    
    public void setText(String text) 
    {
	    editField.setText(text);
    } 

    public int getPreferredWidth() 
    {
        return managerWidth;
    }

    public int getPreferredHeight() 
    {
        return managerHeight;
    }
		
    protected void sublayout(int w, int h) 
    {
        if (managerWidth == 0) 
        {
            managerWidth = w;
        }
        if (managerHeight == 0) 
        {
            managerHeight = h;
        }

        int actWidth = Math.min(managerWidth, w);
        int actHeight = Math.min(managerHeight, h);
        layoutChild (vfm, actWidth - 2, actHeight - 2); // Leave room for border
        setPositionChild (vfm, 1, 1);	              // again, careful not to stomp over the border
        setExtent (actWidth, actHeight);
    }
		
    protected void paint(Graphics g) 
    {
        super.paint(g);
        int prevColor = g.getColor();
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, getWidth(), getHeight());  // draws border
        g.setColor(prevColor);
    }
}


public class HelloBlackBerryScreen extends MainScreen implements Runnable 
{
	public static final boolean DBG = false;

	public static final int MAX_WORD_REVIEW = 50;

	public static final long PERSISTENT_STORE_DEMO_CONTROLLED_ID = 0xbf768b0f3ae726daL; 
	
    Player                      m_player = null;
    private Application         m_application = null;
    private int                 m_timerID = -1;
    private int                 m_currentWordIndex = 0;
    private Vector              m_wordList = null;
    // private Database            m_db = null; //Need BlackBerry code sign certificates.
    private Hashtable           m_wordIndex = null;
    private BasicEditField      m_wordField = null; 
    private TextBoxField        m_wordDefField = null; 

    private String              m_newWordFile = "file:///SDCard/BlackBerry/documents/new_words.txt";
    private String              m_dumpFileName = "file:///SDCard/BlackBerry/documents/dump.txt";
    private String              m_optionFileUrl = "file:///SDCard/BlackBerry/documents/options.txt";
    private String              m_wordSpeakFileName = "file:///SDCard/BlackBerry/music/word.mp3";
    private String              m_wordFile = "file:///SDCard/BlackBerry/documents/dict.yydb";
    private String              m_wordIndexFile = "file:///SDCard/BlackBerry/documents/dict.yyindex"; 
     
    private void DbgLog ( String log )
    {
        System.out.println ( log );
    }

    //implements runnable
    public void run() 
    {
        if( m_timerID != -1 && 
            m_currentWordIndex < m_wordList.size() ) 
        {
            // invalidate();
            m_wordField.setText ( (String)(m_wordList.elementAt( m_currentWordIndex ) ) );
            ++m_currentWordIndex;

            if ( LookupWord (true) )
            {
                SpeakWord ();
            }
        }
        else
        {
            m_application.cancelInvokeLater( m_timerID );
            m_timerID = -1;

            //stop review !
            m_wordDefField.setText ("Word review finish!");
        }
    }

    /*
    // Implement methods in the KeyListener interface for handling keyboard events:
    public boolean keyChar( char key, int status, int time ) 
    {
        if ( key == Characters.ENTER ) 
        {
            return true;
        }
        
        return false;
    }
    
    public boolean keyDown(int keycode, int time) 
    {
        return false;
    }
 
    public boolean keyRepeat(int keycode, int time) 
    {
        return false;
    }
    
    public boolean keyStatus(int keycode, int time) 
    {
        return false;
    }
    
    public boolean keyUp(int keycode, int time) 
    {
        return false;
    }    
    */
    public boolean onSavePrompt ()
    {
        return true; //hide system's default dialog !
    }

    public HelloBlackBerryScreen() 
    { 
        super( MainScreen.VERTICAL_SCROLL | MainScreen.VERTICAL_SCROLLBAR );

        //BasicFilteredList filterList = new BasicFilteredList(); 
        // String m_wordsIndex[] = {""};
        // filterList.addDataSet(1, m_wordsIndex, "all word list",
        //      BasicFilteredList.COMPARISON_IGNORE_CASE);
        // AutoCompleteField m_wordField = new AutoCompleteField(filterList)
        m_wordField = new BasicEditField( "Enter word:", "", 64, 
                Field.SPELLCHECKABLE | BasicEditField.EDITABLE )
        {
            public boolean keyChar(char character, int status, int time) 
            {
                if ( Characters.ENTER == character )
                {
                    LookupWord (false);
                    
                    return true;
                }
                else
                {
                    return super.keyChar ( character, status, time );
                }
            }

            public void paint (Graphics g )
            {
                super.paint (g);
                g.drawRect ( 0, 0, getWidth(), getHeight() );
            }
        };
        add( m_wordField ); 
        
        add( new BasicEditField ("  \nWord define:", "        ",  20, Field.NON_FOCUSABLE)  ); 

        /*m_wordDefField = new BasicEditField("  ", 
                "Please input word and press enter!", MAX_TEXT_NUM, 
                Field.HIGHLIGHT_SELECT | Field.READONLY )
        {
            public void paint (Graphics g )
            {
                super.paint (g);
                g.drawRect ( 0, 0, getWidth(), getHeight() );
            }
        };*/
        m_wordDefField = new TextBoxField ( getWidth(), getHeight(), 
                Field.READONLY | Field.HIGHLIGHT_SELECT );
        add (m_wordDefField);

//        ButtonField searchBtn = new ButtonField( "Speak", 
//                ButtonField.CONSUME_CLICK | ButtonField.FIELD_RIGHT );
//        add( searchBtn );
//        searchBtn.setChangeListener( new FieldChangeListener() 
//            {
//                public void fieldChanged ( Field arg0, int arg1 ) 
//                {
//                    SpeakWord ();
//                }
//            } );
        
        setTitle( "yy dict" );
        // addKeyListener(this);        

        CheckSDCard ();

        m_timerID = -1;
        m_application = Application.getApplication();
    }

    protected void makeMenu( Menu menu, int instance ) 
    {
    	super.makeMenu(menu, instance);

        menu.add( new SpeakMenuItem() );
        menu.add( new LookupMenuItem() );
        menu.add( new ReviewMenuItem() );
        menu.add( new DumpFileMenuItem() );
        menu.add( new SsqMenuItem() );
    }

    private class SpeakMenuItem extends MenuItem 
    {
        public SpeakMenuItem() 
        {
            super( "Speak word", 0, 0);
        }

        public void run() 
        {
            SpeakWord ();
        }
    }

    private class LookupMenuItem extends MenuItem 
    {
        public LookupMenuItem() 
        {
            super( "Lookup word", 1, 0);
        }

        public void run() 
        {
            LookupWord (false);
        }
    }

    private class ReviewMenuItem extends MenuItem 
    {
        public ReviewMenuItem() 
        {
            super( "Review word", 2, 0);
        }

        public void run() 
        {
            ReviewWord ();
        }
    } 

    private class DumpFileMenuItem extends MenuItem 
    {
        public DumpFileMenuItem() 
        {
            super( "Dump to file", 3, 0);
        }

        public void run() 
        {
            DumpFile ();
        }
    } 

    private class SsqMenuItem extends MenuItem 
    {
        public SsqMenuItem() 
        {
            super( "SSQ generator", 4, 0);
        }

        public void run() 
        {
            SsqGenerator ();
        }
    }

    private int GetOptionValue ( String strKey )
    {
        int duration = 5; //default value
        FileConnection fc = null;

        try 
        { 
            fc = (FileConnection)Connector.open(  m_optionFileUrl );

            int filesize = (int)fc.fileSize();
            if ( filesize > 1 )
            { 
                InputStream inputStream = fc.openInputStream (); 
                LineReader lineReader = new LineReader( inputStream );      

                for(;;)
                {
                    try
                    {
                        byte[] line = lineReader.readLine() ; 

                        //format is key=value\n
                        //
                        int idx = indexOf (line, (byte)0x3d, 0);
                        String key = new String(line, 0, idx);
                        if ( key.equals( strKey ) )
                        {
                            duration = parseInt (line, idx+1, line.length ); 
                            break; //find key
                        } 
                    }
                    catch(EOFException eof)
                    { 
                        break;
                    }
                    catch(IOException ioe)
                    {
                        break;
                    }                
                }

                inputStream.close(); 
            } 

            if ( fc != null )
            {
                fc.close(); 
            }
        }
        catch (IOException ioe) 
        {
           ;
        } 

        return duration; 
    }

    private void LoadNewWordFile ()
    {
        // FilePicker maybe need code sign by rim !
        /*
        final FilePicker filePicker = FilePicker.getInstance();
         
        filePicker.setPath(m_reviewWordFileDir); 
        filePicker.setFilter( "txt");
        filePicker.setTitle( "Select a txt file for word review!");

        String fileurl = filePicker.show();
        */
        String fileurl = m_dumpFileName; 
        //"file:///SDCard/BlackBerry/documents/review.txt";

        FileConnection fc = null;

        try 
        { 
            fc = (FileConnection)Connector.open(  fileurl );

            int filesize = (int)fc.fileSize();
            if ( filesize < 1 )
            {
        	    m_wordDefField.setText ( "LoadNewWordFile error:failed open file " + fileurl ); 
            }
            else
            {
                DataInputStream inStream = fc.openDataInputStream(); 
                byte[] buf = new byte[filesize]; 
                inStream.read ( buf, 0, filesize );
                inStream.close(); 

                int wordinfo[] = new int[2];

                m_wordList = new Vector(MAX_WORD_REVIEW, MAX_WORD_REVIEW);

                for (int i = 0, count = 0; count < MAX_WORD_REVIEW; ++ count)
                {
                    getWord ( buf, i, filesize, wordinfo );
                    if ( -1 == wordinfo[0] )
                    {
                        break;
                    }

                    String name = new String(buf, wordinfo[0], wordinfo[1] - wordinfo[0] );

                    // if (DBG) DbgLog ("find word:" + name );

                    m_wordList.addElement ( name );
                    i = wordinfo[1]+1;
                } 
            } 

            if ( fc != null )
            {
                fc.close(); 
            }
        }
        catch (IOException ioe) 
        {
            m_wordDefField.setText ( "LoadNewWordFile error:" + ioe.getMessage() );
        } 
    }

    private void SsqGenerator () 
    {
        Calendar rightNow = Calendar.getInstance(); 
        Random rand = new Random ( rightNow.getTime().getTime()  ); 
        StringBuffer buf = new StringBuffer (1024 );
        
        //FIXME: maybe load from /sdcards/number.txt is better !
        int pool[] = {3,7,8,9,13,14,15,16,19,21,25,26,27,29,31,32,33} ;
        int MAX_NUM = pool.length;
        String name = m_wordField.getText();
        int count = 3; 
        
        if ( name.length() > 0 )
        {
            try 
            {
                count = Integer.parseInt ( name );
                if ( count < 1 ) count = 3;
            }
            catch (NumberFormatException e )
            {
            }
        } 

        buf.append ( "SDZ " );                

        for ( int j = 0; j<count; ++j )
        {
            for (int i = 0; i<6; ++i )
            { 
                int index = rand.nextInt (MAX_NUM-i-1); 
                index += i + 1;

                int tmp = pool[i];
                pool[i] = pool[index];
                pool[index] = tmp;

                if ( pool[i] < 10  )
                {
                    buf.append ( '0' );                
                }
                buf.append ( pool[i] );                
            }                

            buf.append ( '+' );
            buf.append ( rand.nextInt(7) + 10 );
            buf.append ( " " ); 
        } 

        m_wordDefField.setText ( buf.toString() );
    }

    private void DumpFile () 
    {
        String ctx = m_wordDefField.getText();

        WriteFile ( m_dumpFileName, ctx.getBytes(), ctx.length(), false ) ;

    }

    private void ReviewWord () 
    {
        m_currentWordIndex = 0;

        if( -1 == m_timerID ) 
        {
            int duration = GetOptionValue ("duration"); 

            if ( duration < 5)
            {
                duration = 5;
            }

            LoadNewWordFile (); 

            if ( !m_wordList.isEmpty() )
            { 
                m_timerID = m_application.invokeLater( this, duration*1000, true ); 

                m_wordDefField.setText ( "Review" + 
                        Integer.toString( m_wordList.size() ) +
                        " words will start after " +  Integer.toString( duration ) +
                        " seconds" );
            }
        } 
        else if( m_timerID != -1 ) 
        {
            m_application.cancelInvokeLater( m_timerID );
            m_timerID = -1;
        }
    }

    private void SpeakWord () 
    {        
        try 
        {
            if ( null != m_player )
            {
                m_player.close();
            }

            m_player = javax.microedition.media.Manager.createPlayer(
                           m_wordSpeakFileName );
           
            m_player.start();
        }
        catch(MediaException me)
        {
            Dialog.alert( "SpeakWord error: " + me.toString());
        }
        catch(IOException ioe)
        {
            Dialog.alert( "SpeakWord error: " + ioe.toString());
        } 
    }

    private boolean LookupWord (boolean review ) 
    {        
        boolean ret = false;

        String name = m_wordField.getText();
        //String name = m_wordField.getEditField().getText();
        
        // if (DBG) DbgLog ("lookup word:" + name );
        if ( null == m_wordIndex )
        {
            LoadWordFile ();

            if ( null == m_wordIndex )
            {
                m_wordDefField.setText ( "LookupWord error:not found words index\n" );
            }

            return false;
        }

        /*
        try 
        {
            Statement statement = m_db.createStatement(
                    "SELECT word_def FROM ch_words_table WHERE word = ?");                     
            statement.prepare();            
            DbgLog ("parpare statement:" + statement );

            statement.bind(1, name);
            DbgLog ("bind statement:" + statement );

            Cursor cursor = statement.getCursor();                        

            if ( cursor.isEmpty() )
                DbgLog ("getCursor statement return null:" + cursor );
            else
                DbgLog ("getCursor statement return ok:" + cursor );

            if (cursor.next())
            {
                Row row = cursor.getRow();
                wordDef = row.getString (3);
                if ( wordDef != null )
                {
                    m_wordDefField.setText (wordDef);
                }
            }                 
            cursor.close();
            statement.close();
            
            statement = m_db.createStatement(
                    "UPDATE ch_words_table SET lookup_count=lookup_count+1 WHERE word = ?");
            statement.prepare();
            statement.bind(1, name);                                  
            statement.execute(); 
            statement.close(); 
        }
        catch (DataTypeException dexp )
        {
        	m_wordDefField.setText (dexp.toString());
        }  
        catch (DatabaseException dexp )
        {
        	m_wordDefField.setText (dexp.toString());
        }        
        */ 

        WordItem item = (WordItem) m_wordIndex.get (name);
        if ( null == item )
        {
           m_wordDefField.setText ( "LookupWord error:no word found" );

           return false;
        } 

        FileConnection fc = null;

        try 
        { 
            //DbgLog ("get key=" + name );
            //DbgLog ("get offset=" + Integer.toString((item.offset)) );
            //DbgLog ("get audioSize=" + Integer.toString((item.audioSize)) );
            //DbgLog ("get defSize=" + Integer.toString((item.defSize)) );

            fc = (FileConnection)Connector.open( m_wordFile );

            int filesize = (int)fc.fileSize();
            // If no exception is thrown, then the URI is valid, 
            // but the file may or may not exist.
            if ( filesize < 1 )
            {
        	    m_wordDefField.setText ( "LookupWord error:no word found" ); 
            }
            else
            {
                DataInputStream inStream = fc.openDataInputStream(); 

                byte[] wordDef = new byte[ item.defSize + item.audioSize + 8 ];

                inStream.skip (item.offset );
                inStream.read ( wordDef, 0, item.defSize + item.audioSize );
                inStream.close();

                WriteFile ( m_wordSpeakFileName, wordDef, item.audioSize, true );

                String wordStr = new String(wordDef, item.audioSize, item.defSize, "UTF-8") ;
        	    m_wordDefField.setText (wordStr);

                // if (DBG) DbgLog ("word define size:" + Integer.toString((item.defSize)) );
                // if (DBG) DbgLog ("word define:" + wordStr); 

                //add to new word file for review later !
                StringBuffer newword = new StringBuffer(name);
                newword.append(" "); 

                if ( !review )
                {
                    WriteFile ( m_newWordFile, 
                             newword.toString().getBytes(), 
                             newword.length(), false ) ;
                }

                ret = true;
            } 

            if ( fc != null )
            {
                fc.close(); 
            }
        }
        catch (IOException ioe) 
        {
            m_wordDefField.setText ( "LookupWord error:" + ioe.getMessage() );
        } 

        return ret;
    }

    private void CheckSDCard ()
    {
        boolean sdCardPresent = false;
        String root = null;
        Enumeration dirEnum = FileSystemRegistry.listRoots();
        while (dirEnum.hasMoreElements())
        {
            root = (String)dirEnum.nextElement();
            if(root.equalsIgnoreCase("sdcard/"))
            {
                sdCardPresent = true;
            }     
        }            

        if (!sdCardPresent)
        {
            UiApplication.getUiApplication().invokeLater(new Runnable()
                {
                    public void run()
                    {
                        Dialog.alert("This application requires an SD card to be present.");
                        // System.exit(0);            
                    } 
                });        
        }          
        else
        {
            // LoadWordFile ();
        }
    }

    private int parseInt (byte[] arr, int begin, int end )
    {
    	int first = end - 1;
    	int last = begin;
    	int ret = 0;
        int mul = 1;
    	while (first >= last )
    	{
    	    ret += mul*( arr[first] - 0x30);
            mul *= 10;
            --first;
    	}

    	return ret;
    }

    private void getWord (byte[] arr, int pos, int end, int[] wordInfo )
    {
        int i = pos;
        int wordBegin = -1;
        for ( ; i < end; ++i )
        {
            boolean valid = false;
            if ( arr[i] >= 'a' && arr[i] <= 'z' ) 
            {
                valid = true;
            }
            else if ( arr[i] >= 'A' && arr[i] <= 'Z' ) 
            {
                valid = true;
                arr[i] = (byte) (arr[i] + (byte)0x20); //to low case                
            }

            if ( valid )
            {
                if ( -1 == wordBegin )
                {
                    wordBegin = i;
                }
            } 
            else if ( wordBegin >= 0 )
            {
                wordInfo[0] = wordBegin;
                wordInfo[1] = i;
                return ;
            }
        }

        wordInfo[0] = -1;
        wordInfo[1] = -1;
    }

    private int indexOf (byte[] arr, byte c, int pos )
    {
        int i = pos;
        while (i<256)
        {     
            if ( c == arr[i] )
            {
                return i;
            }
            ++i;
        }

        return 0;
    }

    private void BuildWordHashtable ()
    {
        FileConnection fc = null;

        try 
        {
            fc = (FileConnection)Connector.open( m_wordIndexFile );            
            // If no exception is thrown, then the URI is valid, 
            // but the file may or may not exist.
            if ( !fc.exists() )
            {
                m_wordDefField.setText ( "BuildWordHashtable error:load word index file failed!" ); 
            }
            else
            {
                if (DBG) DbgLog ("build hash table start\n");

                m_wordIndex = new Hashtable (25000); 

                InputStream inputStream = fc.openInputStream (); 
                LineReader lineReader = new LineReader( inputStream );      

                for(;;)
                {
                    try
                    {
                        byte[] line = lineReader.readLine() ; 

                        int idx = indexOf (line, (byte)0x2c, 0);
                        int idx2 = indexOf (line, (byte)0x2c, idx+1);
                        int idx3 = indexOf (line, (byte)0x2c, idx2+1); 

                        //DbgLog (" idx=" + Integer.toString(idx) );
                        //DbgLog (" idx2=" + Integer.toString(idx2) );
                        //DbgLog (" idx3=" + Integer.toString(idx3) );
                        
                        //line format:aachen,0,15882,56 
                        String key = new String(line, 0, idx);
                        WordItem item = new WordItem(
                         parseInt (line, idx+1, idx2),
                         parseInt (line, idx2+1,idx3),
                         parseInt (line, idx3+1, line.length ) ); 

                        //DbgLog (" key=" + key );
                        //DbgLog (" offset=" + Integer.toString((item.offset)) );
                        //DbgLog (" audioSize=" + Integer.toString((item.audioSize)) );
                        //DbgLog (" defSize=" + Integer.toString((item.defSize)) );

                        m_wordIndex.put ( key, item ); 

                        // break; 
                    }
                    catch(EOFException eof)
                    {
                        // We've reached the end of the file.
                        m_wordDefField.setText ( "Total words number is:" + Integer.toString (m_wordIndex.size()) );

                        break;
                    }
                    catch(IOException ioe)
                    {
                        m_wordDefField.setText ( "BuildWordHashtable error: " + ioe.getMessage () );
                        break;
                    }                
                }

                inputStream.close();

                if (DBG) DbgLog ("build hash table end\n");
            }

            if ( fc != null )
            {
                fc.close(); 
            }
        }
        catch (IOException ioe )
        {
            m_wordDefField.setText ( "BuildWordHashtable error:" + ioe.getMessage () );
        } 
    }

    private void LoadWordFile ()
    {
        if ( true )
        {                   
            m_wordDefField.setText ( "LoadWordFile, wait a moment!\n" );

            BuildWordHashtable ();

            /*
            PersistentObject obj = PersistentStore.getPersistentObject ( PERSISTENT_STORE_DEMO_CONTROLLED_ID );
            // synchonized (obj)
            {
                m_wordIndex = (Hashtable)obj.getContents();

                if ( null == m_wordIndex )
                {
                    BuildWordHashtable ();

                    if ( null != m_wordIndex )
                    {
                        obj.setContents (m_wordIndex);
                        obj.commit ();
                    }
                }
            } ; */
        }
        // else
        //{
            //NOTE: sqlite require blackberry signed keys
            //
//            String dbLocation = "/SDCard/BlackBerry/documents/ch_words.db"; 
//            
//            try
//            {
//            	URI uri = URI.create(dbLocation); 
//            	m_db = DatabaseFactory.open(uri);
//
//                DbgLog ("load sqlite ok:" + m_db );
//            }
//            catch (IllegalArgumentException e) 
//            {
//				// e.printStackTrace();
//                m_wordDefField.setText ( "IllegalArgumentException error" );
//			} 
//            catch (MalformedURIException e) 
//            {
//				// e.printStackTrace();
//                m_wordDefField.setText ( "MalformedURIException error" );
//			}
//            catch (DatabaseIOException dexp )
//            {
//                m_wordDefField.setText ( dexp.toString() );
//            }
//            catch (DatabasePathException dexp2 )
//            {
//                m_wordDefField.setText ( dexp2.toString() ); 
//            } 
//        }
    }

	private void WriteFile (String filename, byte[] contents, int size, boolean overwrite )
    {
        // if (DBG) DbgLog ( "WriteFile:" + filename );

        FileConnection fc = null;

        try 
        {
            fc = (FileConnection)Connector.open(filename);
            if ( !fc.exists() )
            {
                fc.create();
            }

            if ( overwrite )
            {
                fc.truncate (0); //empty file!

                DataOutputStream outStream = null;
                outStream = fc.openDataOutputStream (); 
                outStream.write ( contents, 0, size );
                outStream.close();
            }
            else
            {
                OutputStream outStream = fc.openOutputStream( fc.fileSize() ); 
                outStream.write ( contents, 0, size );
                outStream.close();
            } 

            if ( fc != null )
            {
               fc.close();
            }
         }
         catch (IOException ioe) 
         {
            DbgLog (ioe.getMessage() );
         } 
    } 
}

//end
//
