package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;

public class DatabaseMigrationTests {

    static Context context;
    static Path rootDbDirectory;

    // data for migration1
    static String initialPersonValueBeforeMigration1 = "1|83fe56ff-1607-4057-8cb0-31f62ccb930f";
    static String adjustedPersonValueAfterMigration1 = "1|83fe56ff-1607-4057-8cb0-31f62ccb930f|Ellis+Katz|1921.NOVEMBER.21|2020.MARCH.12";

    // data for migration2
    static String initialPersonValueBeforeMigration2 = "1|83fe56ff-1607-4057-8cb0-31f62ccb930f|Ellis+Katz|1921.NOVEMBER.21|2020.MARCH.12";
    static String adjustedPersonValueAfterMigration2 = "1|83fe56ff-1607-4057-8cb0-31f62ccb930f|Ellis+Katz|1921.OCTOBER.21|2020.FEBRUARY.12";

    static String ellisPersonFileContents = "1|83fe56ff-1607-4057-8cb0-31f62ccb930f|photo%3Fname%3Db6c3eab6-de63-321e-b24b-30c46dc3626f.jpg|Ellis+Katz|1921-11-21|2020-03-12|%3Ca+href%3Dperson%3Fid%3D4c7363c1-ba8e-422f-8fa6-cbdb2956e275%3EFlorence+Harriet+Katz%3C%2Fa%3E|%3Ca+href%3D%22person%3Fid%3Dab1e7835-e6df-49ac-8492-9cf8b1686d7d%22%3EMarjorie+Katz%3C%2Fa%3E|%3Ca+href%3Dperson%3Fid%3D136c46a8-062b-42a7-b27e-81081e8293a5%3ERobert+Katz%3C%2Fa%3E+and+%3Ca+href%3Dperson%3Fid%3D0727a28f-7e97-4229-9419-a72dbfa77238%3EEthel+Miller+Katz%3C%2Fa%3E|%3Ca+href%3D%22person%3Fid%3Dee3df413-1d4a-41a9-9d40-b597e2d8849e%22%3ERon%3C%2Fa%3E%2C+%3Ca+href%3D%22person%3Fid%3Df0306481-691b-4dd2-a807-2f1784abd509%22%3EDan%3C%2Fa%3E%2C+and+%3Ca+href%3D%22person%3Fid%3D0a75a5e4-8a30-4119-9f80-10b90211b0f1%22%3EPaul%3C%2Fa%3E|%3Cp%3EEllis+was+born+21+November%2C+1921+in+Atlanta%2C+GA.+As+his+Dad+was+a+traveling+salesman%2C+the+family+moved+every+now+and%0D%0A++++again+to+his+base+of+sales%3A+Chattanooga%2C+Chicago%2C+Miami%2C+Philadelphia%2C+Providence...and+back+again+to+Atlanta%0D%0A++++and%2C+then%2C+Jacksonville.+At+one+time%2C+Ellis+and+Marjorie+%28unbeknownst+to+one+another%29+were+in+the+same+elementary%0D%0A++++school+in+Atlanta%3A+the+10th+Street+Elementary+%5Bbut+I%2C+of+course%2C+was+an+%22upper+classman%22%5D+All+through%0D%0A++++elementary+school%2C+Jr.+High+%28O%27Keefe%29%2C+and+part+of+high+school+%28Boy%27s+High%29%2C+his+constant+and+best%0D%0A++++friend+was+Hiram+Horne...his+parents+were+Ellis%27s+parents+and+vice+versa.+It+was+painful+to+leave+him+when%0D%0A++++the+family%2C+in+the+summer+of+%2737+moved+to+Jacksonville.%3C%2Fp%3E%0D%0A%3Cp%3EEllis+attended+Robert+E.+Lee+High+in+Jacksonville%2C+FL+where+he+soon+made+wonderful+friends.+Of+particular+note+is%0D%0A++++Billy+Lasarow+of+Studio+City%3B+he+and+Billy+continue+to+enjoy+long+walks+and+talks+in+the+hills+surrounding+their%0D%0A++++homes.+Ellis%2C+Harvey+Leitman%2C+and+George+%28%22Bubba%22%29+Benjamin+were+among+the+founding+members+of+the%0D%0A++++still-extant+Esquire+Club+and+you+will+note+%28below%29+a+listing+of+the+%22officers%22...and+Ellis+played%0D%0A++++first-string+Left+Tackle+on+the+Lee+championship+football+team+when+they+won+the+Florida+State+%22Big-Ten%22%0D%0A++++Championship+in+1939-40.+%22Arnie%22+%5BSilverberg%5D%2C+my+best+friend%2C+is+also+seen+in+the+%22All+Star%27s%22%0D%0A++++photo%0D%0A%3C%2Fp%3E%0D%0A%3Cp%3EAfter+graduation+from+Lee%2C+Ellis+with+Billy+and+other+close+friends+entered+the+University+of+Florida+in%0D%0A++++Gainesville%2C+Class+of+%2744.+And+they+became+members+of+the+Pi+Lambda+Phi+fraternity+with+experiences+that%0D%0A++++continue+to+afford+many+pleasant+memories.%3C%2Fp%3E%0D%0A%3Cp%3EOf+course%2C+Dec+7%2C+1941+pretty+much+shook+up+things+on+campus+and+a+number+of+%22brothers%27+went+to+war%0D%0A++++but+most+engineering+students%2C+of+which+Ellis+was+one%2C+were+deferred+to+complete+their+studies.+By+the+summer+of%0D%0A++++%2743%2C+Ellis+entered+Georgia+Tech+to+complete+his+last+semester.+After+graduation%2C+he+was+hired+by+Bell%0D%0A++++Aircraft+in+Marietta%2C+GA+as+a+liaison+engineer+for+the+manufacture+of+the+B-29+bomber+aircraft.+As+the+war%0D%0A++++ended%2C+he+happily+took+a+job+in+aeronautical+research+at+the+National+Advisory+Committee+for+Aeronautics+at%0D%0A++++Langley+Field%2C+VA+%28outside+Hampton%29.+The+%22happily%22+word+refers+to+the+fact+that+it+was+from+Hampton%0D%0A++++that+Ellis+was+invited+to+attend+an+afternoon+tea+party+in+Richmond...and+it+was+there%2C+that+Ellis+first+saw%0D%0A++++his+beautiful+Marjorie.%3C%2Fp%3E%0D%0A%0D%0A%3Cimg+src%3D%22photo%3Fname%3Dcd3cb50a-57eb-3fd9-9d71-70ff28ee9262.jpg%22%2F%3E%0D%0A%0D%0A%3Cp%3E%0D%0A++++I+met+the+%22Love+of+My+Life%22%2C+Marjorie+Ruth+Blumberg%2C+at+a+Sunday+Dance%2FTea+Party+in+Richmond%2C%0D%0A++++March+1946%2C+just+as+she+was+completing+her+studies+at+William+%26+Mary%27s+College+of+Fine+Arts+in%0D%0A++++Richmond.+On+the+28th+of+July+1946%2C+in+Chattanooga%2C+her+birthplace%2C+Marjorie+Ruth+married+Ellis+Katz+for%0D%0A++++a+lifetime+of+love%2C+three+sons+and+families%2C+and+happiness.%0D%0A%3C%2Fp%3E%0D%0A%0D%0A%3Cimg+src%3D%22photo%3Fname%3D86b6eb55-8498-3e45-81b4-d29fbcb05271.jpg%22%2F%3E%0D%0A%0D%0A%3Cp%3ERetirement+in+early+87+has+led+to+some+of+the+best+years+of+our+lives.+Marjorie+and+I+have+driven+through%0D%0A++++France%2C+Sicily%2C+Italy%2C+Spain%3B+hiked+the+English+Cotswolds%2C+the+Alps%2C+the+High+Sierra+and+our+local+Santa%0D%0A++++Monicas%3B+joined+the+Calabasas+Golf+%26+Country+Club+where+we+played+lots+of+great+golf+and+celebrated%0D%0A++++our+50th+Annivesary%3B+made+many+new+friends%3B+and+I+joined+the+SAGE+Society+and+have+participed+in+and+led%0D%0A++++many+studies+of+wide-ranging+interest.%3C%2Fp%3E%0D%0A%0D%0A%3Ch2%3EMy+Connection+with+the+Manned+Lunar+Landing+Program+%28APOLLO%29%3C%2Fh2%3E%0D%0A%3Cp%3EMuch+of+my+career+has+been+associated+with+rockets+in+one+form+or+the+other.+It+began+in+1945+with+my+job+as+an%0D%0A++++%22Aerodynamics+Research+Scientist%22+at+the+National+Advisory+Committee+for+Aeronautics+%28NACA%29+at%0D%0A++++Langley+Field%2C+Va.+It+was+there+that+I+began+to+launch+aerodynamic+research+models+on+rocket+boosters+at+Wallop%27s%0D%0A++++Island%2C+just+off+the+east+coast+of+Virginia.+These+rockets+were+surplus+WWII+solid+propellant+units+that+we+used%0D%0A++++to+achieve+supersonic+speeds+for+our+research.+%3C%2Fp%3E%0D%0A%3Cp%3EIn+1951%2C+I+left+the+government+%28NACA%29+for+jobs+in+industry%3A+first+with+Fairchild+Aircraft+Missile+Davison+in+Long%0D%0A++++Island%2C+NY+and%2C+then+in+1955%2C+with+North+American+Aviation%27s+Missile+Division+in+Downey%2C+Ca.%3C%2Fp%3E%0D%0A%3Cp%3EIn+late+1956%2C+at+North+American+Aviation%2C+Downey%2C+I+led+an+advanced+engineering+group+in+a+proposal+to+the+U.S.%0D%0A++++Air+Force+for+a+study+to+create+a+lunar+base+on+the+moon..+The+idea+behind+the+proposal+was+that+a+base+on+the%0D%0A++++far+side+of+the+moon+would+provide+an+ideal+site+for+military+surveillance+and+authority+over+the+entire+face+of%0D%0A++++earth.+The+study+was+not+funded.%3C%2Fp%3E%0D%0A%3Cp%3EThe+Russian+%22Sputnik%22+spacecraft+flew+in+Oct+57.+The+event+had+global+repercussions%2C+especially+in%0D%0A++++the+U.S.%3C%2Fp%3E%0D%0A%3Cp%3EIn+1958+I+briefed+Werner+Von+Braun+at+Huntsville+on+a+proposal+to+use+the+existing+North+American+Navaho+rocket%0D%0A++++booster+for+an+orbital+mission.+The+proposal+was+judged+too+far+ahead+of+its+time.%3C%2Fp%3E%0D%0A%3Cp%3EIn+1959%2C+the+National+Aeronautics+and+Space+Administration+%28NASA%29+was+formed+out+of+the+NACA+to+put+a+man+in%0D%0A++++earth+orbit+before+the+USSR.+The+main+effort+was+led+by+the+staff+of+the+former+Pilotless+Aircraft+Research%0D%0A++++Division+%28PARD%29+at+Langley+Field%2C+VA.+%28As+noted+above%2C+I+was+one+of+the+original+staff+%28circa+1945%29+of+PARD+but%0D%0A++++had+left+the+NACA+by+the+spring+of+1951+to+join+Fairchild+Aviation+in+Long+Island%2C+NY..%29%3C%2Fp%3E%0D%0A%3Cp%3EIn+early+59%2C+I+led+a+North+American+design+team+in+a+study+of+very+large+rocket+boosters%2C+which+would+be+needed%0D%0A++++for+space+missions.+I+presented+our+study+results+to+top+management+at+the+newly-formed+NASA+Headquarters.%3C%2Fp%3E%0D%0A%3Cp%3EIn+1959%2C+in+response+to+an+RFP+%28Request+for+Proposal%29+from+NASA%2C+I+led+a+North+American+engineering+team+to%0D%0A++++design+and+propose+a+large+rocket+booster+for+space+missions.+The+program+was+awarded+to+McDonnell+Aircraft.%3C%2Fp%3E%0D%0A%3Cp%3EIn+1960%2C+following+the+loss+of+the+above+proposal%2C+I+joined+Hughes+Aircraft+as+Director+of+Advanced+Manned+Space%0D%0A++++Programs.+As+Hughes+was+the+leader+in+air+and+space-borne+computer+technology%2C+my+job+was+to+develop+business%0D%0A++++opportunities+for+Hughes+in+the+budding+manned+space+flight+program.+In+that+capacity+I+led+a+study+of+potential%0D%0A++++hazards+during+a+lunar+landing+mission+that+could+require+an+onboard+computing+system+to+safely+return%0D%0A++++astronauts+to+earth.%3C%2Fp%3E%0D%0A%3Cp%3EFrom+my+study+of+lunar+mission+hazards%2C+I+prepared+a+paper%2C+%22A+Mission+Management+Subsystem+for+Advanced%0D%0A++++Manned+Space+Missions%22+which+I+presented+at+the+AIAA+%28American+Institute+of+Aeronautics+and+Astronautics%29%0D%0A++++Annual+Meeting+in+NY+in+Jan+61.%3C%2Fp%3E%0D%0A%3Cp%3EAfter+Gagarin+flew+the+first+manned+space+orbit+in+Apr+61%2C+things+really+heated+up.+In+May+61%2C+President+Kennedy%0D%0A++++announced+a+national+goal+of+placing+a+man+on+the+moon+before+the+end+of+the+decade.+%3C%2Fp%3E%0D%0A%3Cp%3EThe+Space+Task+Group%2C+which+had+been%2C+formed+out+of+PARD+%28see+above%29%2C+succeeded+in+flying+a+manned+sub-orbital%0D%0A++++mission+%28Alan+Shepard%29+in+May+61+and%2C+a+year+later%2C+an+orbital+mission+by+John+Glenn.%3C%2Fp%3E%0D%0A%3Cp%3EIn+late+61%2C+North+American+won+NASA+Apollo+contracts+to+engineer+and+build+the+Command+Module+and+the+second%0D%0A++++stage+%28SII%29+of+the+Apollo+rocket+booster.%3C%2Fp%3E%0D%0A%3Cp%3EFollowing+the+Dec+61+SII+award%2C+North+American+asked+me+to+return+to+participate+in+the+program.+However%2C+it+was%0D%0A++++not+until+a+second+request+%28and+a+much+more+favorable+offer%29+in+Dec+63+that+I+accepted+the+position+of+Director%0D%0A++++of+Systems+Engineering..%3C%2Fp%3E%0D%0A%3Cp%3EDuring+the+next+four+years%2C+I+led+a+large+team+of+engineers+to+resolve+problems+and+interface+issues+regarding%0D%0A++++the+various+systems%2C+%28e.g.%3A+hydraulics%2C+fuel%2C+propulsion%2C+etc.%29+which+made+up+the+Saturn+II+stage.%3C%2Fp%3E%0D%0A%3Cp%3EMy+final+activities+with+regard+to+the+Apollo+Program+came+to+an+abrupt+conclusion+as+a+result+of+the+Apollo+1%0D%0A++++fire+in+Feb+67.+As+a+result+of+what+was+perceived+as+a+national+calamity%2C+all+higher+management+of+the+North%0D%0A++++American+Aviation%27s+Apollo+programs+was+%22fired%22%E2%80%A6including+myself.+The+wholesale+%22firing%22%0D%0A++++was+widely+regarded+as+a+required+catharsis+to+heal+a+tragic+break+in+the+program.+To+compensate%2C+a+job+was%0D%0A++++created+for+me%3A+%22Director+of+Advanced+Launch+Vehicles%22%2C+which+I+held+until+moving+to+Rocketdyne%27s%0D%0A++++High+Energy+Laser+Projects+in+Oct+1980.%3C%2Fp%3E%0D%0A%0D%0A%3Chr%3E%0D%0A%3Ch3%3EA+Love+Story%3C%2Fh3%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D0468bf56-68dc-43c5-aa6a-307870fcb0b3.jpg%22+alt%3D%22group+of+friends%22%3E%3Cfigcaption%3E%0D%0A%3Cp%3ETheir+first+home+was+an+apartment+in+Hampton+where+Ellis+was+an+aeronautical+engineer+at+the+NACA%2C+Langley+Field.+Here+they+are+with+a+group+of+friends+at+an+outing.+Their+special+friends+in+Hampton+were+the+Savages+and+the+Gales+who+later+joined+them+for+their+50th+wedding+anniversary.%0D%0A%3C%2Fp%3E%0D%0A%3Cp%3E%0D%0ATheir+first+home+was+a+small+one-bedroom+garden+apartment+in+Hampton%2C+VA.+Hampton+was+the+site+of+the+National+Advisory+Committee+for+Aeronautics+%7Blater+NASA%29+where+Ellis+was+an+Aerodynamics+Scientist.+They+were+happy+there+and%2Cwithout+a+car%2C+they+walked+and+walked+and+walked%3A+to+%E2%80%9Cdowntown%E2%80%9D+Hampton%2C+to+the+little+inlet+on+the+Hampton+Roads%2C+where%2C+for+a+while%2C+Ellis+docked+a+small+sailboat%2C+to+the+Hampton+Institute+%28an+all-black+college%29+to+see+performances+of+operas%2C+plays%2C+and+concerts.+On+some+occasions%2C+they+would+get+a+ride+with+friends+to+Williamsburg+or+to+the+Blue+Ridge+mountains.%0D%0A%3C%2Fp%3E%0D%0A%3Cp%3E%0D%0AThe+young+couple+made+close+friends+in+Hampton+who+have+remained+dear+throughout+the+years%3A+Phyl+and+Mel+Savage+and+Harriet+and+Larry+%28now+gone%29+Gale%E2%80%A6who+now+live+in+Boca+Raton+FL.+Both+couples+were+here+to+celebrate+their+50th+wedding+anniversary+in+1996.%0D%0A%3C%2Fp%3E%0D%0A%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3Deefbcb4f-e849-4bdf-937e-6b4561dc5fe7.jpg%22+alt%3D%22with+ron%22%3E%3Cfigcaption%3ERon+came+along+on+28+Feb+50+and+their+little+family+was+on+its+way.++They+moved+out+of+their+one-bedroom+flat+in+Hampton+to+a+%E2%80%9Cspacious%E2%80%9D+two-bedroom+apartment+in+Newport+News.+By+this+time+they+had+a+car+of+their+own%3A+a+49+Ford%21%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D05bf93ec-f9f4-4d8b-a418-dd5de6784068.jpg%22+alt%3D%22levittown%2C+NY%22%3E%3Cfigcaption%3E%0D%0A%3Cp%3E%0D%0AIn+May+51%2C+they+moved+to+Levittown%2C+NY%2C+where+Ellis+began+work+for+Fairchild+Aircraft+Guided+Missile+Division.++After+a+year%2C+they+rented+a+small+house+on+St+Marks+Lane+in+Islip+%28farther+out+on+the+island%29.++They+were+very+happy+there+and+decided+to+build+a+home+nearby.++Marjorie+and+Ellis+were+thrilled+to+finally+have+their+own+home+%28below%29%2C+but%2C+unfortunately%2C+Fairchild+began+to+lose+business.++In+May+55%2C+Ellis+was+offered+a+promising+position+by+North+American+Aviation+in+Downey%2C+CA+and%2C+so+after+one+year+in+their+new+home%2C+they+moved+to+California.%0D%0A%3C%2Fp%3E%0D%0A%3Cp%3E%0D%0AIn+May+of+1951%2C+Ellis+was+offered+an+attractive+job+at+Fairchild+Guided+Missiles+in+Wyandanch%2C+NY%E2%80%A6So+the+couple%2C+with+son+Ron%2C+traveled+to+Long+Island+and+rented+a+Levittown+house+for+a+year.+It+was+the+first+time+Ellis+gave+in+to+the+idea+of+watching+TV%E2%80%A6the+house+had+a+built-in+unit.+One+year+later%2C+they+moved+to+a+rented+house+in+Islip%2C+a+community+far+out+from+the+city+and+where+the+couple+felt+they+would+want+to+live+for+the+rest+of+their+lives.+So+they+began+to+build+a+home+in+Islip+and%2C+each+day%2C+Marj+and+Ron+%28in+his+little+red+pedal+car%29+would+go+the+several+blocks+to+the+building+site+and+watch+the+construction.+It+became+a+lovely+home+and+the+family+moved+in+by+the+fall+of+1954.%0D%0A%3C%2Fp%3E%0D%0A%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D33989c10-3653-40ed-9b39-7ffdf8597282.jpg%22+alt%3D%22sons3%22%3E%3Cfigcaption%3E%3Cp%3EUnfortunately%2C+by+that+time%2C+Fairchild+had+begun+to+hit+the+skids+and%2C+by+early+1955%2C+Ellis+was+enticed+to+accept+a+job+offer+from+North+American+in+Downey%2C+CA.+Also%2C+by+that+time%2C+Marjorie+was+%E2%80%9Cwith+child%E2%80%9D%E2%80%A6.to+become+Danny.%0D%0A%3C%2Fp%3E%0D%0A%3Cp%3E%0D%0AWhile+Ellis+traveled+to+California+in+May+%E2%80%9955+to+find+a+home+and+begin+work+at+North+American%2C+Marjorie+with+Ron+traveled+to+Dalton%2C+GA+%28her+parents+had+been+living+there+for+years+as+proprietors+of+a+jewelry+store%29+to+give+birth+on+24+June+to+second+son%2C+Daniel+Marshall.%0D%0A%3C%2Fp%3E%0D%0A%3Cp%3E%0D%0AWhittier+is+a+lovely+Quaker-founded+town+to+the+east+of+Los+Angeles.+The+family+lived+near+a+beautiful+WPA-constructed+park+that+was+like+the+family%E2%80%99s+playground.+Very+soon+Marj+and+Ellis+made+good+friends+of+Florence+and+Harold+Ehlers+and+some+of+Ellis%E2%80%99s+co-workers.+Harold%2C+to+this+day%2C+is+one+of+their+closest+friends+and+living+in+San+Luis+Obispo.%0D%0A%3C%2Fp%3E%0D%0A%3Cp%3E%0D%0AOn+20+Sep+%E2%80%9958%2C+Marj+gave+birth+to+third+son%2C+Paul+Elliott%2C+as+happy+a+%E2%80%9Chappening%E2%80%9D+as+could+ever+be.+Grandparents%2C+Bob+and+Ethel+Katz%2C+were+overjoyed+to+be+present+at+that+wonderful+moment.+God+had+been+good%E2%80%A6the+family+was+happy+and+healthy+and+active.+There+were+excursions+%28often+with+the+Ehlers%29+to+the+beaches%2C+to+the+mountains+and+snow%2C+to+Sequoia+National+Forest%2C+to+Big+Sur%2C+and+to+Mexico.+Ron+became+the+leading+pitcher+for+the+baseball+Little+League+of+Whittier+and%2C+later%2C+in+Encino.%0D%0A%3C%2Fp%3E%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D6eaa5e76-060d-4608-b76e-1df3ceeafbf1.jpg%22+alt%3D%22whole_fam%22%3E%3Cfigcaption%3EAs+Marjorie+was+about+to+give+birth+to+her+second+child%2C+she+traveled+to+Dalton%2C+GA%2C+to+be+with+her+parents+where+on%2C+24+June+55%2C+Dan+was+born.++Ellis%2C+meantime%2C+had+found+a+house+on+Penn+St.+in+Whittier+and%2C+so+by+July+55%2C+the+family+was+reunited.++The+family+enjoyed+nearby+Penn+Park+and+became+good+friends+with+the+Ehlers+family+who+lived+nearby.++On+occasion+there+were+trips+to+Baja%2C+Mexico%2C+to+the+beaches+and+to+nearby+mountains.++But+a+special+treat+was+being+able+to+visit+with+Ellis%E2%80%99s+parents+who+had+moved+to+Los+Angeles+back+in+51.++By+20+Sep+58%2C+Paul+rounded+out+the+happy+family.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D22525199-7e86-4a2d-9351-b339b1555147.jpg%22+alt%3D%22marj+and+baby%22%3E%3Cfigcaption%3EAt+the+time%2C+Ron+was+10%2C+Dan+5%2C+and+Paul+2.+Marj+was+always+at+home+to+see+that+the+boys+were+provided+for%2C+to+see+them+off+to+school%2C+to+prepare+their+lunches%2C+to+be+home+for+them+when+they+returned+from+school%2C+to+take+them+to+the+park%2C+to+prepare+the+family+dinner.+Dinnertime+was+very+special+for+the+family%3B+it+was+more+than+a+time+of+delicious+food%E2%80%A6it+was+also+a+time+for+conversation+about+what+had+happened+at+school%2C+what+happened+in+the+city+or+country%2C+and+a+time+to+reflect+upon+events.+It+was+most+special+on+Friday+nights+%28the+%E2%80%9CShabbat%E2%80%9D%29+when+Ellis+would+arrive+home+early+from+work%2C+and+the+family+would+dress+for+dinner+in+the+Dining+Room%2C+and+Marj+would+light+the+candles+and+say+the+blessings.+Those+beautiful+moments+remain+with+us+all.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3Dcede777e-c7db-46f5-8efd-55d66e8706b0.jpg%22+alt%3D%22encino%22%3E%3Cfigcaption%3EIn+May+60%2C+Ellis+was+offered+a+substantial+promotion+by+Hughes+Aircraft+in+Culver+City.++As+the+commute+from+Whittier+was+difficult%2C+on+Labor+Day+60%2C+the+family+moved+into+their+present+home+on+Hayvenhurst+Drive+in+Encino.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3Dd8a5c578-8cc4-40d8-81a2-3fd8d100377c.jpg%22+alt%3D%22explore+the+mountains%22%3E%3Cfigcaption%3EEncino+became+a+very+happy+home+for+the+family.+They+became+active+members+of+Temple+Judea%2C+made+many+friends%2C+explored+the+beautiful+mountains+and+seacoasts+and+have+been+blessed+with+health+and+prosperity.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D9340b098-acda-4ac9-aa20-c0cd1a721423.jpg%22+alt%3D%22engineering+manager%22%3E%3Cfigcaption%3EIn+1963+Ellis+returned+to+North+American+Aviation+as+an+engineering+manager+on+the+Apollo+%5BNASA%E2%80%99s+Manned+Lunar+Landing%5D+Program.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D1ffadaa0-ec03-4295-adc7-0c782f44918f.jpg%22+alt%3D%22self_portrait%22%3E%3Cfigcaption%3EDuring+all+the+years+of+raising+her+family%2C+Marjorie+had+put+off+her+love+for+creating+fine+art.+Now%E2%80%A6she+took+up+her+her+brushes+and+began+to+display+and+sell+her+works+in+local+art+galleries.+The+photo+is+of+Marjorie+standing+before+her+self-portrait+at+a+local+art+show.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D9764a529-daec-4be7-85bd-e65c7b48bac2.png%22+alt%3D%22havurah+group%22%3E%3Cfigcaption%3EThere+were+many+happy+moments+with+friends%E2%80%A6their+Havurah+group+from+Temple+Judea+%28below+on+an+outing+to+Napa+Valley%29+and+Marjorie%E2%80%99s+Investorette+stock+%28and+social%29++club%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D36d02a71-36cf-4c68-853a-f2e904c69487.jpg%22+alt%3D%22walking+and+hiking%22%3E%3Cfigcaption%3EWalking+and+hiking+were+always+a+part+of+their+lives.++They+have+hiked+the+High+Sierra%2C+the+Cotswold%E2%80%99s+%28England%29%2C+the+Swiss+Alps%2C+and+trails+in+Spain%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3Df84bbcbe-4919-448b-98d6-cf485b5af779.jpg%22+alt%3D%22amalfi+coast%22%3E%3Cfigcaption%3EAnd+there+were+adventurous+driving+trips+through+the+UK%2C+France%2C+Spain%2C+Italy%2C+Sicily%2C+and+Switzerland.++Here+on+Italy%E2%80%99s+Amalfi+Coast%2C+they+enjoy+a+scene+with+their+friends%2C+the+Goodman%E2%80%99s.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D0669461c-5062-4db5-b673-935230ed0254.jpg%22+alt%3D%22brothers%22%3E%3Cfigcaption%3E%E2%80%A6And+their+sons+grew+and+prospered.++Ron+became+a+Memphis+entrepreneur+and+businessman%2C+Dan+created+a+successful+advertising+company%2C+and+Paul+became+an+attorney-at-law.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3Dcac94381-b302-4b61-8c58-60eaa3b373cb.jpg%22+alt%3D%22daughters%22%3E%3Cfigcaption%3E%E2%80%A6And+here+is+where+their+%E2%80%9Ccup+did+truly+runneth+over%E2%80%9D%21++Their+sons+gave+to+Marjorie+and+Ellis+three+darling+daughters%3A+Susan%2C+Michelle+and+Tina+who+brought+beauty+and+love+into+their+family%E2%80%A6+and+four+grandchildren%3A+Byron%2C+Elysa%2C+Erica+and+Joelle.++And+Byron%2C+with+Dearest+Susanne%2C+living+in+Atlanta%2C+has+now+added+a+great+grandson%2C+Cameron%2C+while+Elysa+has+married+Dan+Simone+and+lives+in+Portland%2C+OR.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3Db51c7fa1-18d7-4889-a92b-73899da8fc64.jpg%22+alt%3D%2250th%22%3E%3Cfigcaption%3EBut+the+most+wonderful+moment+of+all+for+the+happy+couple+was+their+children%E2%80%99s+gift+of+their+50th+Wedding+party+held+at+the+Calabasas+Country+Club.++It+was+a+festive+occasion+with+their+family+and+friends+from+far+and+wide.%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Cimg+src%3D%22photo%3Fname%3D6d808901-273c-44b0-965e-0daf875d855e.jpg%22+alt%3D%22brothers+at+50th%22%3E%0D%0A%3Cimg+src%3D%22photo%3Fname%3D1a279944-a929-4bb2-bc46-bb5102221c2c.jpg%22+alt%3D%22singing%22%3E%0D%0A%3Cimg+src%3D%22photo%3Fname%3D303a923f-80cc-421f-8156-20963718aff4.jpg%22+alt%3D%22girls1%22%3E%0D%0A%0D%0A%3Cfigure%3E%3Cimg+src%3D%22photo%3Fname%3D9a634054-ec5d-4e87-b74e-41dc3e68a6dc.jpg%22+alt%3D%22love+story%22%3E%3Cfigcaption%3EYes%E2%80%A6and+for+sixty-six+%E2%80%9Cstory+book%E2%80%9D+years%2C+Marjorie+and+Ellis+have+lived%E2%80%A6and+do+live%E2%80%A6a+%E2%80%9CLove+Story%E2%80%9D%3C%2Ffigcaption%3E%3C%2Ffigure%3E%0D%0A%0D%0A%3Ca+href%3D%22https%3A%2F%2Fwww.elliskatz.net%2F%22%3Eelliskatz.net%3C%2Fa%3E";
    static Path ellisPersonFile;
    private static ILogger logger;
    private static IFileUtils fileUtils;
    private static FileUtils minumFileUtils;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("_unit_tests");
        MemoriaContext memoriaContext = MemoriaContext.buildMemoriaContext(context);
        fileUtils = memoriaContext.fileUtils();
        minumFileUtils = new FileUtils(context.getLogger(), context.getConstants());
        logger = context.getLogger();
        rootDbDirectory = Path.of(context.getConstants().dbDirectory + "migration_tests");
    }

    @AfterClass
    public static void cleanup() {
        minumFileUtils.deleteDirectoryRecursivelyIfExists(rootDbDirectory);
    }

    @Before
    public void beforeEach() throws IOException {
        minumFileUtils.deleteDirectoryRecursivelyIfExists(rootDbDirectory);
        fileUtils.makeDirectory(rootDbDirectory.resolve("persons"));
        fileUtils.makeDirectory(rootDbDirectory.resolve("person_files"));
        fileUtils.makeDirectory(rootDbDirectory.resolve("sessions"));

        // write Ellis's person_file - the voluminous content about a person
        Files.writeString(rootDbDirectory.resolve("person_files")
                .resolve("83fe56ff-1607-4057-8cb0-31f62ccb930f"),
                ellisPersonFileContents);

        // write a session file - key details about a session - see SessionId
        Files.writeString(rootDbDirectory.resolve("sessions")
                        .resolve("1.ddps"),
                "1|D0CpybIyKFkQBV9acfPU|2023-08-12T18%3A20%3A20.468114064Z%5BUTC%5D|1");
    }

    @Test
    public void testShouldMigrate1() throws IOException {
        // write Ellis's initial person db file
        Path personsDirectory = rootDbDirectory.resolve("persons");
        ellisPersonFile = personsDirectory.resolve("1.ddps");
        Files.writeString(ellisPersonFile, initialPersonValueBeforeMigration1);

        var migration1 = new Migration1(rootDbDirectory, context.getLogger());
        migration1.run();
        assertEquals(Files.readString(ellisPersonFile), adjustedPersonValueAfterMigration1);

        migration1.runReverse();
        assertEquals(Files.readString(ellisPersonFile), initialPersonValueBeforeMigration1);

        migration1.run();
        assertEquals(Files.readString(ellisPersonFile), adjustedPersonValueAfterMigration1);

        migration1.runReverse();
        assertEquals(Files.readString(ellisPersonFile), initialPersonValueBeforeMigration1);

        migration1.run();
        assertEquals(Files.readString(ellisPersonFile), adjustedPersonValueAfterMigration1);
    }

    @Test
    public void testShouldMigrate2() throws IOException {
        // write Ellis's initial person db file
        Path personsDirectory = rootDbDirectory.resolve("persons");
        ellisPersonFile = personsDirectory.resolve("1.ddps");
        Files.writeString(ellisPersonFile, initialPersonValueBeforeMigration2);

        var migration2 = new Migration2(rootDbDirectory, context.getLogger());
        migration2.run();
        assertEquals(Files.readString(ellisPersonFile), adjustedPersonValueAfterMigration2);

        migration2.runReverse();
        assertEquals(Files.readString(ellisPersonFile), initialPersonValueBeforeMigration2);

        migration2.run();
        assertEquals(Files.readString(ellisPersonFile), adjustedPersonValueAfterMigration2);

        migration2.runReverse();
        assertEquals(Files.readString(ellisPersonFile), initialPersonValueBeforeMigration2);

        migration2.run();
        assertEquals(Files.readString(ellisPersonFile), adjustedPersonValueAfterMigration2);
    }

    @Test
    public void testShouldMigratePersonFiles() throws IOException {
        // write Ellis's initial personFile db file
        Path personFilesDirectory = rootDbDirectory.resolve("person_files");
        ellisPersonFile = personFilesDirectory.resolve("83fe56ff-1607-4057-8cb0-31f62ccb930f");
        var migration3 = new Migration3(rootDbDirectory, context.getLogger());
        migration3.run();
        var migration4 = new Migration4(rootDbDirectory, context.getLogger());
        migration4.run();
        var migration5 = new Migration5(rootDbDirectory, context.getLogger());
        migration5.run();
        var migration8 = new Migration8(rootDbDirectory, context.getLogger());
        migration8.run();
        var migration9 = new Migration9(rootDbDirectory, context.getLogger());
        migration9.run();
        var migration11 = new Migration11(rootDbDirectory, context.getLogger());
        migration11.run();
        // migration 11 should add a new field for the last modifier of this person, defaulting to "admin"
        assertEquals(PersonFile.EMPTY.deserialize((Files.readString(ellisPersonFile))).getBorn().toHtmlString(), "1921-11-21");
        assertTrue(PersonFile.EMPTY.deserialize((Files.readString(ellisPersonFile))).getLastModified().toString().length() > 5);
        assertEquals(PersonFile.EMPTY.deserialize((Files.readString(ellisPersonFile))).getLastModifiedBy(), "admin");
        assertEquals(deserializeHelper(Files.readString(ellisPersonFile)).size(), 16);
        var migration12 = new Migration12(rootDbDirectory, context.getLogger());
        migration12.run();
        // migration 12 should replace all references to .png with .jpg
        assertFalse(Files.readString(ellisPersonFile).contains(".png"));
        migration11.runReverse();
        assertEquals(deserializeHelper(Files.readString(ellisPersonFile)).size(), 15);
        migration9.runReverse();
        migration8.runReverse();
        migration5.runReverse();
        assertEquals(deserializeHelper(Files.readString(ellisPersonFile)).size(), 13);
        migration4.runReverse();
        assertEquals(deserializeHelper(Files.readString(ellisPersonFile)).size(), 12);
        migration3.runReverse();
        assertEquals(deserializeHelper(Files.readString(ellisPersonFile)).size(), 11);
    }

    @Test
    public void testShouldMigrateSessions() throws IOException {
        Path sessionsDirectory = rootDbDirectory.resolve("sessions");
        var sessionFile = sessionsDirectory.resolve("1.ddps");
        var migration6 = new Migration6(rootDbDirectory, context.getLogger());
        migration6.run();
        assertEquals(deserializeHelper(Files.readString(sessionFile)).size(), 5);
        var migration10 = new Migration10(rootDbDirectory, context.getLogger());
        migration10.run();
        assertEquals(deserializeHelper(Files.readString(sessionFile)).toString(), "[1, D0CpybIyKFkQBV9acfPU, 2023-08-12T18:20:20.468114064Z, 2023-08-12T18:20:20.468114064Z, 1]");
        migration10.runReverse();
        assertEquals(deserializeHelper(Files.readString(sessionFile)).toString(), "[1, D0CpybIyKFkQBV9acfPU, 2023-08-12T18:20:20.468114064Z[UTC], 2023-08-12T18:20:20.468114064Z[UTC], 1]");
        migration6.runReverse();
        assertEquals(deserializeHelper(Files.readString(sessionFile)).size(), 4);
    }

    @Test
    public void testMigration7() throws IOException {
        fileUtils.makeDirectory(rootDbDirectory.resolve("photo_to_person"));
        Files.writeString(rootDbDirectory.resolve("photo_to_person").resolve("1.ddps"), "1|99|1");
        Migration7 migration7 = new Migration7(rootDbDirectory, context.getLogger(), context);

        migration7.run();

        // wait for the actionQueue to process and for the disk to handle the write
        MyThread.sleep(10);
        assertFalse(Files.exists(rootDbDirectory.resolve("photo_to_person").resolve("1.ddps")));
    }

    /**
     * We will not use anything but JPEG's now.  This tests that old
     * photos with .png suffix get converted to .jpg
     */
    @Test
    public void testMigration13_photos() throws IOException {
        fileUtils.makeDirectory(rootDbDirectory.resolve("photos"));
        Files.writeString(rootDbDirectory.resolve("photos").resolve("1.ddps"), "1|ac0db8be-e678-42a6-bedc-c793640d4d9f.png|gravestone+translation|");
        Migration13 migration13 = new Migration13(rootDbDirectory, context.getLogger());

        migration13.run();

        // wait for the actionQueue to process and for the disk to handle the write
        MyThread.sleep(10);
        String convertedFile = Files.readString(rootDbDirectory.resolve("photos").resolve("1.ddps"));
        assertFalse(convertedFile.contains(".png"));
        assertEquals(convertedFile, "1|ac0db8be-e678-42a6-bedc-c793640d4d9f.jpg|gravestone+translation|");
    }

    @Test
    public void testMigration14_photo_conversion() throws IOException {
        minumFileUtils.deleteDirectoryRecursivelyIfExists(rootDbDirectory.resolve("photo_archive"));
        fileUtils.makeDirectory(rootDbDirectory.resolve("photo_archive"));
        Files.copy(
                Path.of("sample_db/simple_db/photo_archive/86b6eb55-8498-3e45-81b4-d29fbcb05271.jpg"),
                rootDbDirectory.resolve("photo_archive").resolve("86b6eb55-8498-3e45-81b4-d29fbcb05271.jpg")
                );

        Migration14 migration14 = new Migration14(rootDbDirectory, context.getLogger(), context);

        migration14.run();
        MyThread.sleep(300);
    }

}
