# 4e Compendium Downloader #

## Version 3.5.3 development branch ##

![Screenshot of program](https://raw.githubusercontent.com/Sheep-y/trpg-dnd-4e-db/master/res/img/History%20-%20v3.5%20viewer.png)

This app can be used to reterives and locally stores entries from online 4e [D&D Compendium](http://www.wizards.com/dndinsider/compendium/database.aspx).
 <br/>
Stored entries can be exported, then browsed and searched with exact phase search, wildcast, exclusion, join ("OR"), and even regular expression.

This is a fan-made project and does not come with copyrighted data.

## How To Download Data ##

1. You need an active [Dungeons & Dragons Insider subscription](http://ddi.wizards.com/) to retrieve data.
   1. This may be impossible since registration is closed. See [in-app help](http://htmlpreview.github.io/?https://github.com/Sheep-y/trpg-dnd-4e-db/blob/development/res/downloader_about.html#Troubleshoot) for links.
2. [Download](http://www.java.com/) and install Java (version 8 or above).
3. [Download](https://github.com/Sheep-y/trpg-dnd-4e-db/releases/) the downloader exe (Windows) or downloader jar (Linux/Mac).
4. Open a folder for the downloader, put it in, and run it.
   1. Jar version: If double clicking the jar file does not work, open console and run "java -jar 4e_compendium_downloader.jar". Note that it is a GUI program.
5. In the downloader, fill in DDI username and password, then click "Download".
   1. Download can be stopped and resumed any time.
   2. See [in-app help](http://htmlpreview.github.io/?https://github.com/Sheep-y/trpg-dnd-4e-db/blob/development/res/downloader_about.html) for steps and troubleshoots.
6. Once all data is downloaded, you can export the data to an HTML file, which can be opened in browsers .

### New columns ###

This downloader will create new data columns, unavailable in official compendium, for easier search:

* Background: Replace Associated Skills column with a new Benefit column.
* Theme: New column, Prerequisite.
* Power: New columns, Type (Frequency + Type) and Keyword.
* Feat: New column, Prerequisite.
* Item: New column, Type.  New categories: Weapon, Implement, and Armor.
* Terrain: New columns, Group and Level.  Combined with Trap.

Many existing columns are also enhanced or corrected,
such as listing all sources in the source column,
or filling in missing data such as subrace info.

### Fixing compendium errors ###

Let's face it, the official compendium has errors.
This downloader fixes over 710 entries during export:

* 150 items missing power frequency. (Dantrag's Bracers, many energy weapons, dragon orbs, light sources etc.)
* 79 empty glossaries, removed. (male, female, fang titan drake, etc.)
* 67 entries without "published" record. (Granny's Grief, Dreamheart, Deck of Many Things etc.)
* 60 entries moved to correct category. (Superior implements, Assassin's poisons, and Consumable wondrous)
* 35 entries that wrongly says "basic melee attack". (Bane's Tactics, Feral Armor, Dancing Weapon, Kobold Piker etc.)
* 22 entries with formatting issues. (Mirror of Deception, Silver Hands of Power, etc.)
* 20 entries with missing/wrong content. (Kord's Relentlessness, Rings of the Akarot, Hybrid Vampire Surge etc.)
* 10 typos. (Cambion Stalwart "bit points", Primal Grove "grp", Rubble Topple "Singe", Essential hybrid sourcebook etc.)
* 76 entries with other inconsistent content. (Winged Armor type, Xenda-Dran Array tier, Racial power without type etc.)
* 52 entries with missing listing data not caused by above issues. (Subrace abilities, Heroic feat tier etc.)
* 182 entries with wrong/inconsistent listing data not caused by above issues. (Multi-source classes, traps roles etc.)
* In addition, over a hundred artifacts and item sets are manually classified.

If you find similar mistakes that aren't fixed, please [file an issue](https://github.com/Sheep-y/trpg-dnd-4e-db/issues/).

## Source code and building ##

This section is for programmers only.
Average user should download the [ready-made program](https://github.com/Sheep-y/trpg-dnd-4e-db/releases/).

* Viewer source code is in html folder.
* Downloader source code is in java folder, and use libraries in java_lib folder.
* Both use resources at the root (license) and in the resource folder.
* Use Ant (build.xml) to 'make' an executable jar.  The make_exe target depends on [Launch4j](http://launch4j.sourceforge.net/).
* The jar can also be extracted to a new project folder; use Ant to move the extracted files back to original structure.

The viewer is built with [CocoDoc](https://github.com/Sheep-y/CocoDoc/) app builder, which is bundled and must run in GUI.

[SQLJet](https://sqljet.com/) is used to access sqlite database, but the data cannot be read by other SQLite libraries.
It may be a sqljet issue.
Since it is very light and has no other issues, I'm keeping it and hoping someone can find what's wrong someday.

<small>
Code, documentations, and related resources are open source and licensed under <a href="https://www.gnu.org/licenses/agpl-3.0.en.html">GNU AGPL v3</a>. <br/>
D&D and Dungeons & Dragons are trademarks of Wizards of the Coast LLC.
</small>