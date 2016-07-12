package db4e;

import db4e.data.Category;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import sheepy.util.Utils;
import sheepy.util.ui.ConsoleWebView;
import sheepy.util.ui.ObservableArrayList;

/**
 * Data Management
 */
public class Downloader {

   private static final Logger log = Main.log;

   private static final String DB_NAME = "dnd4_compendium.sqlite";

   // Database variables are set on open().
   // Access must be synchronised with 'this'
   private SqlJetDb db;
   private DbAbstraction dal;

   public final ObservableList<Category> categories = new ObservableArrayList<>();

   private final SceneMain gui;
   private final ConsoleWebView browser;
   private final Timer scheduler = new Timer();

   public Downloader ( SceneMain main ) {
      gui = main;
      browser = main.getWorker();
   }

   /////////////////////////////////////////////////////////////////////////////
   // Open, Close, and Reset
   /////////////////////////////////////////////////////////////////////////////

   void resetDb () {
      gui.disallowAction( "Clearing data" );
      categories.clear();

      ForkJoinPool.commonPool().execute( () -> { try {
         synchronized ( this ) { // Lock database for the whole duration
            closeDb();
            Thread.sleep( 1000 ); // Give OS some time to close the handle
            final File file = new File( DB_NAME );
            if ( file.exists() ) {
               log.log( Level.INFO, "Deleting database {0}", new File( DB_NAME ).getAbsolutePath() );
               file.delete();
            } else {
               log.log( Level.WARNING, "Database file not found: {0}", new File( DB_NAME ).getAbsolutePath() );
            }
            Thread.sleep( 500 ); // Give OS some time to delete the file
            open( null );
         }

      } catch ( Exception ex ) {
         log.log( Level.WARNING, "Error when deleting database: {0}", Utils.stacktrace( ex ) );
         open( null ).whenComplete( ( a, b ) -> gui.setStatus( "Cannot clear data" ) );

      } } );
   }

   CompletableFuture<Void> open( TableView categoryTable ) {
      gui.disallowAction( "Opening Database" );

      return CompletableFuture.runAsync( () -> {
         try {
            log.log( Level.INFO, "Opening database {0}", DB_NAME );
            synchronized( this ) {
               db = SqlJetDb.open(new File( DB_NAME ), true );
               dal = new DbAbstraction();
            }
         } catch ( Exception ex ) {
            log.log( Level.SEVERE, "Cannot open database: {0}", Utils.stacktrace( ex ) );
            gui.disallowAction( "Cannot open database" );
            gui.btnClearData.setDisable( false );
            closeDb();
            throw new RuntimeException( ex );
         }

         log.log( Level.FINE, "Database opened. Loading tables." );
         if ( categoryTable != null ) categoryTable.setItems( categories );
         openOrCreateTable();
      } );
   }

   void close() {
      scheduler.cancel();
      closeDb();
   }

   private synchronized void closeDb () {
      if ( db != null ) try {
         log.log( Level.FINE, "Closing database" );
         db.close();
         db = null;
         dal = null;
      } catch ( Exception ex ) {
         log.log( Level.WARNING, "Error when closing database: {0}", Utils.stacktrace( ex ) );
      }
   }

   private synchronized void openOrCreateTable() {
      try {
         int version = dal.setDb( db, categories );
         log.log( Level.CONFIG, "Database version {0,number,#}.  Tables opened.", version );

      } catch ( Exception e1 ) {

         log.log( Level.CONFIG, "Create tables because {0}", Utils.stacktrace( e1 ) );
         try {
            dal.createTables();
            int version = dal.setDb( db, categories );
            log.log( Level.FINE, "Created and opened tables.  Database version {0,number,#}.", version );

         } catch ( Exception e2 ) {
            log.log( Level.SEVERE, "Cannot create tables: {0}", Utils.stacktrace( e2 ) );
            gui.disallowAction( "Cannot open database, try clear data" );
            gui.btnClearData.setDisable( false );
            closeDb();
            throw new RuntimeException( e2 );
         }
      }
      gui.allowAction( "Ready to go" );
   }

   /////////////////////////////////////////////////////////////////////////////
   // Download
   /////////////////////////////////////////////////////////////////////////////

   void startDownload () {
      gui.disallowAction( "Opening online compendium" );

      // Open compendium
      CompletableFuture<Void> dbOpen = new CompletableFuture<>();

      Platform.runLater( () -> {
         TimerTask openTimeout = Utils.toTimer( () -> { synchronized( browser ) {
            log.warning( "Open compendium timeout." );
            browser.handle( null, null );
            dbOpen.completeExceptionally( new TimeoutException( "Online compendium timeout" ) );
         } } );
         scheduler.schedule( openTimeout, 30*1000 );

         browser.handle( (e) -> { synchronized( browser ) {
            log.info( "Compendium opened." );
            openTimeout.cancel(); browser.handle( null, null );
            dbOpen.complete( null );

         } }, (e,err) -> { synchronized( browser ) {
            log.log( Level.WARNING, "Open compendium error: {0}", Utils.stacktrace( err ) );
            openTimeout.cancel(); browser.handle( null, null );
            dbOpen.completeExceptionally( err );
         } } );
//         browser.getWebEngine().load( "http://www.wizards.com/dndinsider/compendium/database.aspx" );
         browser.getWebEngine().load( "http://127.0.0.1/" ); // Test error
      } );

      dbOpen.thenComposeAsync( ( result ) -> {
         gui.setStatus( "Loading categories" );
         return null;
      } ).exceptionally( (err) -> {
         if ( err instanceof Exception )
            gui.allowAction( ( (Exception) err ).getMessage() );
         else
            gui.allowAction( err.getClass().getSimpleName() );
         return null;
      });
   }
}