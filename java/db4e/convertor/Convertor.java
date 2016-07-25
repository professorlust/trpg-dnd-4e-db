package db4e.convertor;

import db4e.Main;
import db4e.controller.ProgressState;
import db4e.data.Category;
import db4e.data.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convert category and entry data for export
 */
public class Convertor {

   protected static final Logger log = Main.log;
   protected static final List<Entry> corrected = new ArrayList<>();

   protected final Category category;
   protected final boolean debug;

   /**
    * Called before doing any export.
    * Can be used to fix entry count before catalog is saved.
    *
    * @param categories
    */
   public static void beforeConvert ( List<Category> categories ) {
      categories.stream().filter( category -> category.id.equals( "Glossary" ) ).findAny().get().blacklisted_entry.set( 1 );
      synchronized( corrected ) {
         corrected.clear();
      }
   }

   public static void afterConvert () {
      synchronized( corrected ) {
         log.log( Level.INFO, "Corrected {0} entries", corrected.size() );
      }
   }

   protected Convertor ( Category category, boolean debug ) {
      this.category = category;
      this.debug = debug;
   }

   public static Convertor getConvertor ( Category category, boolean debug ) {
      switch ( category.id ) {
         case "Ritual":
         case "Monster":
         case "Trap":
         case "Poison":
         case "Disease":
            return new LeveledConvertor( category, debug );
         case "Companion":
         case "Terrain":
            return new FieldSortConvertor( category, 0, debug ); // Sort by first field
         case "Feat":
            return new FeatConvertor( category, debug );
         case "Item":
            return new ItemConvertor( category, debug );
         case "Power":
            return new PowerConvertor( category, debug );
         default:
            return new Convertor( category, debug );
      }
   }

   public void convert ( ProgressState state ) {
      if ( category.meta == null )
         category.meta = category.fields;

      initialise();

      final List<Entry> entries = category.entries;
      for ( Entry entry : entries ) {
         if ( entry.content == null ) throw new IllegalStateException( entry.name + " (" + category.name + ") has no content" );
         convertEntry( entry );
         state.addOne();
      }

      if ( category.sorted == null ) {
         category.sorted = entries.toArray( new Entry[ entries.size() ] );
         Arrays.sort( category.sorted, this::sortEntity );
      }

      if ( category.sorted.length != category.total_entry.get() )
         throw new IllegalStateException( "Sorted entry count mismatch with total" );
   }

   protected void initialise()  { }

   protected int metaIndex ( String field ) {
      return Arrays.asList( category.meta ).indexOf( field );
   }

   protected int sortEntity ( Entry a, Entry b ) {
      return a.name.compareTo( b.name );
   }

   private final Matcher regxCheckFulltext = Pattern.compile( "<\\w|(?<=\\w)>|&[^D ]" ).matcher( "" );
   private final Matcher regxCheckOpenClose = Pattern.compile( "<(/?)(p|span|b|i)\\b" ).matcher( "" );
   private final Matcher regxCheckDate  = Pattern.compile( "\\(\\d+/\\d+/\\d+\\)" ).matcher( "" );
   private final Map<String, Entry> shortId = new HashMap<>();

   /**
    * Apply common conversions to entry data.
    * entry.meta may be set, but other prorerties will be overwritten.
    *
    * @param entry
    */
   protected void convertEntry ( Entry entry ) {
      entry.display_name = entry.name.replace( "’", "'" );
      entry.shortid = entry.id.replace( ".aspx?id=", "" );
      copyMeta( entry );
      entry.data = normaliseData( entry.content );
      if ( correctEntry( entry ) != null ) synchronized ( corrected ) {
         corrected.add( entry );
      }
      if ( "null".equals( entry.shortid ) ) return;
      parseSourceBook( entry );
      entry.fulltext = textData( entry.data );

      if ( debug ) {
         // These checks are enabled only when debug log is showing, mainly for development and debug purpose.
         if ( shortId.containsKey( entry.shortid ) )
            log.log( Level.WARNING, "{1} duplicate shortid '{2}': {3} & {0}", new Object[]{ entry.id, entry.name, entry.shortid, shortId.get( entry.shortid ).name } );
         else
            shortId.put( entry.shortid, entry );

         // Validate content tags
         if ( entry.data.contains( "<img " ) || entry.data.contains( "<a " ) )
            log.log( Level.WARNING, "Unremoved image or link in {0} {1}", new Object[]{ entry.shortid, entry.name } );

         int unclosed_p = 0, unclosed_span = 0, unclosed_b = 0, unclosed_i = 0;
         regxCheckOpenClose.reset( entry.data );
         while ( regxCheckOpenClose.find() ) {
            switch( regxCheckOpenClose.group( 2 ) ) {
               case "p":    unclosed_p += regxCheckOpenClose.group( 1 ).isEmpty() ? 1 : -1 ; break;
               case "span": unclosed_span += regxCheckOpenClose.group( 1 ).isEmpty() ? 1 : -1 ; break;
               case "b":    unclosed_b += regxCheckOpenClose.group( 1 ).isEmpty() ? 1 : -1 ; break;
               case "i":    unclosed_i += regxCheckOpenClose.group( 1 ).isEmpty() ? 1 : -1 ; break;
            }
         }
         if ( ( unclosed_p | unclosed_span | unclosed_p | unclosed_i ) != 0 )
            log.log( Level.WARNING, "Unbalanced open and closing bracket in {0} ({1})", new Object[]{ entry.shortid, entry.name } );

         // Validate fulltext
         if ( regxCheckFulltext.reset( entry.fulltext ).find() )
            log.log( Level.WARNING, "Unremoved html tag in fulltext of {0} ({1})", new Object[]{ entry.shortid, entry.name } );
         if ( regxCheckDate.reset( entry.fulltext ).find() )
            log.log( Level.WARNING, "Unremoved errata date in fulltext of {0} ({1})", new Object[]{ entry.shortid, entry.name } );
         if ( ! entry.fulltext.endsWith( "." ) ) // Item144 & Item152 fails this check
            log.log( Level.WARNING, "Not ending in full stop: {0} ({1})", new Object[]{ entry.shortid, entry.name } );
      }
   }

   protected void copyMeta ( Entry entry ) {
      if ( entry.meta != null ) return;
      final int length = entry.fields.length;
      entry.meta = new Object[ length ];
      System.arraycopy( entry.fields, 0, entry.meta, 0, length );
   }

   // Entry specific content data fixes. No need to call super when overriden.
   protected Object correctEntry ( Entry entry ) {
      switch ( category.id ) {
      case "Glossary":
         switch ( entry.shortid ) {

         case "glossary679":
            // Familiar - an empty "monster keyword" from Dungeon 211. That don't even has a stat block.
            return entry.shortid = "null"; // Just blacklist it and forget it ever existed.

         } return null;

      case  "Poison":
         entry.data = entry.data.replace( "<p>Published in", "<p class=publishedIn>Published in" );
         switch ( entry.shortid ) {

         case "poison19": // Granny's Grief
            return entry.data = entry.data.replace( ">Published in .<", ">Published in Dungeon Magazine 211.<" );

         } return null;

      case "Monster":
         switch ( entry.shortid ) {

         case "monster2248": // Cambion Stalwart
            return entry.data = entry.data.replace( "bit points", "hit points" );

         } return null;
      }
      return null;
   }

   private static final Map<String, String> books = new HashMap<>();

   static {
      books.put( "Adventurer's Vault", "AV" );
      books.put( "Adventurer's Vault 2", "AV2" );
      books.put( "Arcane Power", "AP" );
      books.put( "Dark Sun Campaign Setting", "DSCS" );
      books.put( "Dark Sun Creature Catalog", "DSCC" );
      books.put( "Divine Power", "DP" );
      books.put( "Dragons of Eberron", "DoE" );
      books.put( "Draconomicon: Chromatic Dragons", "Draconomicon: Chromatic" );
      books.put( "Draconomicon: Metallic Dragons", "Draconomicon: Metallic" );
      books.put( "Dungeon Delve", "DD" );
      books.put( "Dungeon Master's Guide", "DMG" );
      books.put( "Dungeon Master's Guide 2", "DMG2" );
      books.put( "Dungeon Master's Kit", "DMK" );
      books.put( "E1 Death's Reach", "E1" );
      books.put( "E2 Kingdom of the Ghouls", "E2" );
      books.put( "E3 Prince of Undeath", "E3" );
      books.put( "Eberron Campaign Setting", "ECS" );
      books.put( "Eberron Player's Guide", "EPG" );
      books.put( "FR1 Scepter Tower of Spellgard", "FR1" );
      books.put( "Forgotten Realms Campaign Guide", "FRCG" );
      books.put( "Forgotten Realms Player's Guide", "FRPG" );
      books.put( "H1 Keep on the Shadowfell", "H1" );
      books.put( "H2 Thunderspire Labyrinth", "H2" );
      books.put( "H3 Pyramid of Shadows", "H3" );
      books.put( "HS1 The Slaying Stone", "HS1" );
      books.put( "HS2 Orcs of Stonefang Pass", "HS2" );
      books.put( "Heroes of Shadow", "HoS" );
      books.put( "Heroes of the Elemental Chaos", "HotEC" );
      books.put( "Heroes of the Fallen Lands", "HotFL" );
      books.put( "Heroes of the Feywild", "HotF" );
      books.put( "Heroes of the Forgotten Kingdoms", "HotFK" );
      books.put( "Into the Unknown: The Dungeon Survival Handbook", "DSH" );
      books.put( "Manual of the Planes", "MotP" );
      books.put( "Martial Power", "MP" );
      books.put( "Martial Power 2", "MP2" );
      books.put( "Monster Manual", "MM" );
      books.put( "Monster Manual 2", "MM2" );
      books.put( "Monster Manual 3", "MM3" );
      books.put( "Monster Vault", "MV" );
      books.put( "Monster Vault: Threats to the Nentir Vale", "MV:TttNV" );
      books.put( "Mordenkainen's Magnificent Emporium", "MME" );
      books.put( "Neverwinter Campaign Setting", "NCS" );
      books.put( "P1 King of the Trollhaunt Warrens", "P1" );
      books.put( "P2 Demon Queen Enclave", "P2" );
      books.put( "P3 Assault on Nightwyrm Fortress", "P3" );
      books.put( "Player's Handbook", "PHB" );
      books.put( "Player's Handbook 2", "PHB2" );
      books.put( "Player's Handbook 3", "PHB3" );
      books.put( "Player's Handbook Races: Dragonborn", "PHR:D" );
      books.put( "Player's Handbook Races: Tiefling", "PHR:T" );
      books.put( "Primal Power", "PP" );
      books.put( "Psionic Power", "PsP" );
      books.put( "PH Heroes: Series 1", "PHH:S1" );
      books.put( "PH Heroes: Series 2", "PHH:S2" );
      books.put( "Red Box Starter Set", "Red Box" );
      books.put( "Rules Compendium", "RC" );
      books.put( "The Plane Above", "TPA" );
      books.put( "The Plane Below", "TPB" );
      books.put( "The Shadowfell", "TS" );
      books.put( "Vor Rukoth: An Ancient Ruins Adventure Site", "Vor Rukoth" );
   }

   private final Matcher regxPublished = Pattern.compile( "<p class=publishedIn>Published in ([^<>]+)</p>" ).matcher( "" );
   private final Matcher regxBook = Pattern.compile( "([A-Z][^,.]*)(?:, page[^,.]+|\\.)" ).matcher( "" );

   protected void parseSourceBook ( Entry entry ) {
      if ( regxPublished.reset( entry.data ).find() ) {

         String published = regxPublished.group( 1 );
         StringBuilder sourceBook = new StringBuilder();
         String lastSource = "";
         regxBook.reset( published );
         while ( regxBook.find() ) {
            String book = regxBook.group( 1 ).trim();
            String abbr = books.get( book );
            if ( abbr == null ) {
               if ( book.equals( "Class Compendium" ) ) continue; // Never published
               if ( book.contains( " Magazine " ) )
                  abbr = book.replace( "gon Magazine ", "" ).replace( "geon Magazine ", "" );
               else {
                  books.put( book, book );
                  log.log( Level.FINE, "Source without abbrivation: {0} ({1})", new Object[]{ book, entry.shortid } );
                  abbr = book;
               }
            }
            if ( sourceBook.length() > 0 ) sourceBook.append( ", " );
            sourceBook.append( abbr );
            lastSource = abbr;
         }
         if ( lastSource.isEmpty() )
            if ( published.equals( "Class Compendium." ) )
               lastSource = "CC"; // 11 feats and 2 powers does not list any other source book, only class compendium.
            else
               log.log(Level.WARNING, "Entry with unparsed book: {0} {1} - {2}", new Object[]{ entry.shortid, entry.name, published} );
         entry.meta[ entry.meta.length-1 ] = sourceBook.indexOf( ", " ) > 0 ? sourceBook.toString() : lastSource;

      } else if ( entry.data.contains( "ublished in" ) ) {
         log.log( Level.WARNING, "Entry with unparsed source: {0} {1}", new Object[]{ entry.shortid, entry.name } );
      } else {
         log.log( Level.INFO, "Entry without source book: {0} {1}", new Object[]{ entry.shortid, entry.name } );
      }
   }

   // Products, Magazines of "published in". May be site root (Class Compendium) or empty (associate.93/Earth-Friend)
   //private final Matcher regxSourceLink = Pattern.compile( "<a href=\"(?:http://www\\.wizards\\.com/[^\"]+)?\" target=\"_new\">([^<]+)</a>" ).matcher( "" );
   // Internal entry link, e.g. http://www.wizards.com/dndinsider/compendium/power.aspx?id=2848
   //private final Matcher regxEntryLink = Pattern.compile( "<a href=\"http://www.wizards.com/dndinsider/compendium/[^\"]+\">([^<]+)</a>" ).matcher( "" );
   // Internal search link, e.g. http://ww2.wizards.com/dnd/insider/item.aspx?fid=21&amp;ftype=3 - may also be empty (monster.2508/Darkpact Stalker)
   //private final Matcher regxSearchLink = Pattern.compile( "<a target=\"_new\" href=\"http://ww2.wizards.com/dnd/insider/[^\"]+\">([^<]*)</a>" ).matcher( "" );
   // Combined link pattern
   private final Matcher regxLinks = Pattern.compile( "<a(?: target=\"_new\")? href=\"(?:http://ww[w2].wizards.com/[^\"]*)?\"(?: target=\"_new\")?>([^<]*)</a>" ).matcher( "" );

   private final Matcher regxAttr = Pattern.compile( "<([^<>\"]+) (\\w+)=\"(\\w+)\">" ).matcher( "" );

   protected String normaliseData ( String data ) {
      // Replace images with character. Every image really appears in the compendium.
      data = data.replace( "<img src=\"images/bullet.gif\" alt=\"\">", "✦" ); // Four pointed star, 11x11, most common image at 100k hits
      data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/x.gif\">", "✦" ); // Four pointed star, 7x10, second most common image at 40k hits
      if ( data.contains( "<img " ) ) { // Most likely monsters
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/S2.gif\">", "(⚔)" ); // Basic melee, 14x14
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/S3.gif\">", "(➶)" ); // Basic ranged, 14x14
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/Z1.gif\">" , "ᗕ" ); // Blast, 20x20, for 10 monsters
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/Z1a.gif\">", "ᗕ" ); // Blast, 14x14
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/Z2a.gif\">", "⚔" ); // Melee, 14x14
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/Z3a.gif\">", "➶" ); // Ranged, 14x14
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/Z4.gif\">",  "✻" ); // Area, 20x20
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/Z4a.gif\">", "✻" ); // Area, 14x14
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/aura.png\" align=\"top\">", "☼" ); // Aura, 14x14
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/aura.png\">", "☼" ); // Aura, 14x14, ~1000?
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/1a.gif\">", "⚀" ); // Dice 1, 12x12, honors go to monster.4611/"Rort, Goblin Tomeripper"
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/2a.gif\">", "⚁" ); // Dice 2, 12x12, 4 monsters got this
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/3a.gif\">", "⚂" ); // Dice 3, 12x12, ~30
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/4a.gif\">", "⚃" ); // Dice 4, 12x12, ~560
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/5a.gif\">", "⚄" ); // Dice 5, 12x12, ~2100
         data = data.replace( "<img src=\"http://www.wizards.com/dnd/images/symbol/6a.gif\">", "⚅" ); // Dice 6, 12x12, ~2500
      }
      // Convert spaces and breaks
      data = data.replace( "&nbsp;", "\u00A0" );
      data = data.replace( "<br/>", "<br>" ).replace( "<br />", "<br>" );
      data = regxSpaces.reset( data ).replaceAll( " " );
      // Convert ’ to ' so that people can actually search for it
      data = data.replace( "’", "'" );
      // Convert attribute="value" to attribute=value, for cleaner data
      data = regxAttr.reset( data ).replaceAll( "<$1 $2=$3>" );
      // Convert some rare line breaks
      if ( data.indexOf( '\n' ) >= 0 ) {
         data = data.replace( "\n,", "," );
         data = data.replace( "\n.", "." );
         data = data.replace( ".\n", "." );
      }

      // Remove links
      //data = regxSourceLink.reset( data ).replaceAll( "$1" );
      //data = regxEntryLink .reset( data ).replaceAll( "$1" );
      //data = regxSearchLink.reset( data ).replaceAll( "$1" );
      data = regxLinks.reset( data ).replaceAll( "$1" );

      return data.trim();
   }

   private final Matcher regxPowerFlav = Pattern.compile( "(<h1 class=\\w{5,9}power>.*?</h1>)<p class=flavor><i>[^>]+</i></p>" ).matcher( "" );
   private final Matcher regxItemFlav  = Pattern.compile( "(<h1 class=mihead>.*?</h1>)<p class=miflavor>[^>]+</p>" ).matcher( "" );
   // Errata removal. monster217 has empty change, and many have empty action (Update/Added/Removed).
   private final Matcher regxErrata  = Pattern.compile( "<br>\\w* \\([123]?\\d/[123]?\\d/20[01]\\d\\)<br>[^<]*" ).matcher( "" );
   private final Matcher regxHtmlTag = Pattern.compile( "</?\\w+[^>]*>" ).matcher( "" );
   private final Matcher regxSpaces  = Pattern.compile( " +" ).matcher( " " );

   /**
    * Convert HTML data into full text data for full text search.
    *
    * @param data Data to strip
    * @return Text data
    */
   protected String textData ( String data ) {
      // Removes excluded text
      if ( data.indexOf( "power>" ) > 0 ) // Power flavour
         data = regxPowerFlav.reset( data ).replaceAll( "$1" );
      if ( data.indexOf( "mihead>" ) > 0 ) // Magic item flavour
         data = regxItemFlav.reset( data ).replaceAll( "$1" );
      data = data.replace( "<p class=publishedIn>Published in", "" ); // Source book
      data = regxErrata.reset( data ).replaceAll( " " ); // Errata

      // Strip HTML tags then redundent spaces
      data = data.replace( '\u00A0', ' ' );
      data = regxHtmlTag.reset( data ).replaceAll( " " );
      data = regxSpaces.reset( data ).replaceAll( " " );

      // HTML unescape. Compendium has relatively few escapes.
      data = data.replace( "&amp;", "&" );
      data = data.replace( "&gt;", ">" ); // glossary.433/"Weapons and Size"

      return data.trim();
   }
}