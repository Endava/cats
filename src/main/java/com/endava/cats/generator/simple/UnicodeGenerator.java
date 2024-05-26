package com.endava.cats.generator.simple;

import com.endava.cats.util.CatsUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Holds fuzzing payloads focused on unicode characters fuzzing.
 */
public abstract class UnicodeGenerator {

    private static final List<String> INVALID_REFERENCES = List.of("?", "??", "/?/", "\u0000", "\u200B", "%", "&", "/.. ;/",
            "../", ".. /", ".. ;/", "%5c..%5c.%5c", ".././", "%09", "..%00/", "..%0d/", "..%5c/", "..%ff/", ";.json", ".json");
    private static final String ZALGO_TEXT = " ̵̡̡̢̡̨̨̢͚̬̱̤̰̗͉͚̖͙͎͔͔̺̳͕̫̬͚̹͖̬̭̖̪̗͕̜̣̥̣̼͍͉̖͍̪͈̖͚̙͛͒͂̎̊̿̀̅̈͌͋̃̾̈̾̇͛͌͘͜͜͠͝ͅͅͅ ̷͕̗̇͛̅̀̑̇̈͗͌͛̐̀͆̐̊̅̋̈́̂̈́̈́͑̓͂͂̌̈́̽͌͐̐͂͐̈́̍̂͗̂͘͠͝͝͝ͅ ̷̨̢̧̢̡̨̛͕̯̭̹͖̮̘̤̩̥̟̖͈̯̠̖͈̜͈̥̫͔̘̭͉͎͇̤̦̯͙̹̠̼̮͕̲̖̟̲̦̣͇̳͖̳̭͇͓̭͌̓̀̅̋̋̀̈́̎̄͛̾̊͐̎̉̏͊͐̑͊͒̐̔̏̔̋̑̌͆̏̀̉͆̆́̓̆̉̀̒̆̆̉̀̂̎̈̔͗̔̕̕͘̕̚̚̕͘͜͝͝͝͝͝͠ͅ ̷̧̡̥͈͓͙͈̫͙͎͈̻̔̊̎̏̑̒̐̐̆̉̍͠͝͝ ̴̡̛̛͓͎͇̘͈͇̱̟̠̳͇̬̺̲̭̪̬̼̝̠̙̹̩̱̪͔͉͎̱͚͍̬͈̤͈͙͖̝̲̦̞̺̟̟̺͇̳͈̠̘̺̪̱̮̉̀̍̏̐̃̅̐̊̾͆̐͋͊̿̉̆̾͊̀͊͒͌̀͛̎́́͂̐͂̎͛̆͜͜͜͠ͅ ̶̧̧͖̻̥̝̺̼̙̫̩̹̣̲̩̲͍̺̘͕̤͉̹̥͉̮̮̟̘̥̺̯̗̠͈̬͚̦̦͚̫̫̦̉́̾̀̅͋̋̇̕̕͜͜͝ͅͅ ̶̧̛̛̝̟̤̬̙͔̻͙͚̹̣̳̳͔̥̘̠̗̦̠͚͎̖̮̳̗̥̫͚̯̬̩̎́̽͒̋̓̀͂̈́̓́̎͐͊͒̎͒͌̿̿̔͐̈́͑̊̄̓̎͐̓̓̍͘̕̚̚͜͜ ̶̢̡̡̨̡̡̘̫̫̠̟̻̳̻͈̲̖͚͇̼̩̥̥͎̥̯͚̞̘̼̞͍̮̗͈̱͚͙̠͔̞̮̱̭͍͍̪̲̜͓͍̣̯̲̠̲̤̅͊̑̇̆́̈́̓̿̄̐̓̐͐́͛̆͜͝͝͝͠ͅ ̶̧̡̨̧̡̧̥̥̱̪͇̞̭͙͚͔̜̠͓͈̞͈̣̹̝̩̦̟̻̰͙̯̼̜̞̮̬̝͚̺̟͎̻̱̙̦̜̭̲̰͎̳̣̈͜͜͜ͅ ̸̹̟̯̝͚̪̼͓͕͕̹͖̣̠͓̫͇͚͔̼̊́͑̊̊̅͗͠ͅ";
    @Getter
    private static final List<String> spacesHeaders = Arrays.asList(" ", "\u0009");
    private static final List<String> whitespacesHeaders = Arrays.asList(
            "\u1680", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006", "\u2007", "\u2008", "\u2009",
            "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000", "\u00A0");
    private static final List<String> whitespacesFields = Arrays.asList(
            " ", "\u1680", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006",
            "\u2007", "\u2008", "\u2009", "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000", "\u00A0");
    @Getter
    private static final List<String> controlCharsHeaders = Arrays.asList(
            "\r\n", "\u0000", "\u0007", "\u0008", "\n", "\u000B", "\u000C", "\r", "\u200B", "\u200C", "\u200D", "\u200E",
            "\u200F", "\u202A", "\u202B", "\u202C", "\u202D", "\u202E", "\u2060", "\u2061", "\u2062", "\u2063", "\u2064", "\u206D", "\u0015",
            "\u0016", "\u0017", "\u0018", "\u0019", "\u001A", "\u001B", "\u001C", "\u001D", "\u001E", "\u001F", "\u007F", "\u0080", "\u0081",
            "\u0082", "\u0083", "\u0085", "\u0086", "\u0087", "\u0088", "\u008A", "\u008B", "\u008C", "\u008D", "\u0090", "\u0091", "\u0093",
            "\u0094", "\u0095", "\u0096", "\u0097", "\u0098", "\u0099", "\u009A", "\u009B", "\u009C", "\u009D", "\u009E", "\u009F", "\uFEFF", "\uFFFE", "\u00AD");
    @Getter
    private static final List<String> controlCharsFields = Stream.concat(controlCharsHeaders.stream(), Stream.of("\u0009")).toList();

    private static final String BAD_PAYLOAD = "퀜\uD80C\uDE1B\uD859\uDCBC\uD872\uDC4F璫骋\uD85B\uDC0F\uD842\uDF46\uD85D\uDC7C\uD85C\uDC71\uD884\uDC2E\uD854\uDCA4\uD861\uDE98ྶ\uD85E\uDCD4ᠰ\uD86F\uDC65榬\uD849\uDC0D" +
            "\uD863\uDE3B\uD869\uDD16뜍\uD83A\uDD20૰촩\uD84F\uDF32\uD86F\uDEFAẇ\uD842\uDEB0ў璵\uD854\uDF4Fい\uD85A\uDC86\uD861\uDECC帩쫲\uD822\uDFAC\uD876\uDC73\uD85D\uDEB9환\uD86B\uDC4C旳ᥛ\uD852\uDCD0ࣝ" +
            "\uD863\uDF3A\uD850\uDD22\uD878\uDC82哐꿥㾁纬\uD875\uDC5F\uD879\uDD17\uD872\uDCBB\uD83E\uDDE0\uD866\uDC00쮓\uD804\uDC69\uD884\uDD1E땨\uD84F\uDED9\uD841\uDCFE\uD880\uDF5B\uD82C\uDD0A㊟韻悈씜\uD860" +
            "\uDD69\uD872\uDE81\uD882\uDE40\uD84B\uDE7D\uD86A\uDC48\uD87A\uDDEA睽\uD81A\uDCD8\uD81D\uDF6B\uD844\uDCD0閙폮ꀪ帽㧁\uD85A\uDD03ℇ\uD854\uDDA2\uD842\uDC86\uD802\uDF1C慱퉸畬Ŀ\uD84D\uDD7D蜍呙촠靧\uD877" +
            "\uDE5C\uD801\uDF4C馔\uD83E\uDF23\uD821\uDC9B鷶\uD86B\uDDA0\uD801\uDEA6␢\\\\u0093\uD86D\uDFD5\uD85F\uDFD5ꔻ梈\uD867\uDDB3\uD806\uDE9F磱斉\uD870\uDF88\uD80C\uDD71\uD85B\uDFA1\uD802\uDDD6\uD86C\uDD25뮉" +
            "\uD803\uDE8A\uD80D\uDC1B\uD867\uDCFF\uD801\uDEB4꓃\uD845\uDD6A\uD858\uDD73\uD86A\uDFB3䞸⩦蓟씫\uD80C\uDFAF\uD879\uDF2A졈\uD849\uDF73\uD873\uDD4B首跰\uD878\uDC38\uD83E\uDE43\uD848\uDDE2뽰\uD882\uDFBE뗂" +
            "\uD873\uDC20ﵔ\uD857\uDD34\uD871\uDF21\uD843\uDE59\uD855\uDE55\uD863\uDE47↡콖ꢋ\uD861\uDE35徖厑凧\uD83A\uDD3F竜\uD882\uDE24㎑\uD852\uDFBC\uD82C\uDDCE\uD876\uDDEA\uD81B\uDF14\uD86B\uDFD1쏛\uD876\uDF51" +
            "\uD86D\uDC6D怅\uD876\uDDC1ᅼ甂嵒\uD873\uDDE9⇩Υ\uD86B\uDD24㈙\uD850\uDEEE\uD81F\uDC84憥㦽፯\uD86D\uDC94\uD81D\uDE0E泉\uD884\uDCA6\uD86B\uDD68\uD861\uDDE1\uD850\uDFDA㓮\uD85A\uDEC5꣼\uD853\uDC8C\uD86C" +
            "\uDF83\uD85F\uDC42⋳၇\uD85F\uDCD8䎰\uD852\uDF4F\uD85E\uDC7D\uD83C\uDC3E蘋\uD864\uDCD3\uD846\uDD11륈\uD873\uDC2D\uD869\uDC82\uD872\uDE5D\uD856\uDF1F⑳䂇䛼齅\uD872\uDDB4譚\uD863\uDE05\uD862\uDE4C" +
            "\uD882\uDF73\uD877\uDEA7砠\uD835\uDE7E\uD873\uDE46灏\uD85B\uDCCA¾\uD85E\uDD13齫㪘\uD846\uDE42㝹⻀⬼\uD871\uDDAE\uD805\uDF0C땳\uD860\uDC47\uD848\uDC83\uD864\uDCDB袀ł\uD843\uDDE3\uD849\uDDC4Ţꀸ" +
            "\uD854\uDC5F뒌\uD855\uDEB9\uD808\uDF12\uD860\uDE13\uD807\uDC35덝褖\uD86A\uDC7E䵅\uD860\uDD13\uD847\uDF0E\uD847\uDF9D㴅䍵\uD870\uDD85\uD86C\uDEBD\uD85E\uDE75俴\uD858\uDEF3\uD834\uDD40\uD858" +
            "\uDFD2柘\uD811\uDD93\uD845\uDFE0\uD81A\uDCDE呱\uD876\uDF25\uD84A\uDC4B\uD847\uDE6B鰢팭\uD848\uDDF7㉢肬\uD860\uDDC9\uD87E\uDD5C\uD879\uDCC7誛垕\uD80C\uDD9A\uD87E\uDDFB쫚\uD83D\uDC09㱷ꪉ겱沒듰孅헔汱蛻휱榤䣔ꃅ" +
            "\uD86D\uDCAE\uD862\uDCD4犔\uD848\uDC6F⬖쉨ꞔ㺺\uD855\uDD11\uD85D\uDF44\uD81A\uDD32\uD861\uDE32釸\uD862\uDFD8\uD850\uDE95㶟\uD859\uDCDF\uD86D\uDF1Eꜷ堈\uD85D\uDCCA遡泏\uD86A\uDC01ᇵ\uD863\uDDE8⭝" +
            "\uD85E\uDFD2⊥\uD852\uDF96\uD862\uDE36\uD85D\uDC2E㪌☁\uD802\uDCAC\uD822\uDC20漶\uD878\uDC66\uD84D\uDD26\uD852\uDFE1\uD850\uDF68ϲ槏\uD876\uDF65ꗐ\uD859\uDEC1邢曝捰\uD870\uDC20\uD83C\uDFC7뤆ᩤ" +
            "\uD81C\uDC9D\uD836\uDD98⫩ꂪ亻\u0560\uD85B\uDCDB\uD85F\uDFA7\uD865\uDF01.帼剐\uD85C\uDD28엡\uD867\uDC2D捾\uD863\uDE27\uD87E\uDCBD\uD838\uDED9\uD843\uDEAD\uD854\uDF7E\uD864\uDEF5\uD87A" +
            "\uDD5B\uD86A\uDD26땬\u1CA7왌㚕\uD849\uDF6C\uD835\uDCF2盢㡦\uD83D\uDF6B\uD81C\uDF05\uD841\uDC03\uD870\uDE9E\uD805\uDDCF\uD857\uDE0E\uD869\uDF4A\uD836\uDDA8\uD866\uDE4D\uD873\uDF93⪢忔㫯ᨴ" +
            "\uD86C\uDC2Dۿ냆惔呆\uD877\uDC4E\uD84B\uDF97祫벝嬘ᐅ걱銝༄\uD835\uDDEB掙\uD861\uDCE6솛茞偺\uD855\uDDD1\uD855\uDC6C\uD867\uDD0B\uD853\uDFB8䜮ઞ\uD875\uDD70\uD84C\uDD80\uD852\uDD39\uD85A\uDEC4" +
            "\uD873\uDDC4\uD804\uDE2Fﳒ疵౦\uD863\uDF9F\uD85A\uDD5Eꮤ\uD86A\uDD20\uD865\uDFC6뾇\uD834\uDDDE烏\uD834\uDE15\uD83A\uDC5E\uD869\uDD5B\uD842\uDD24\uD808\uDE52彷᭚א\uD86E\uDDA7\uD881\uDF23" +
            "\uD854\uDDFA\uD81C\uDFA2\uD85B\uDF1C\uD800\uDF09⭮\uD86A\uDC13\uD86F\uDC87㖱톗捿웆\uD875\uDED2駆\uD871\uDD8Dಌ\uD85D\uDDF9\uD874\uDE26垈㾆\uD873\uDD87䪀幒\uD844\uDE2C\uD840\uDE21鮗\uD872" +
            "\uDCF3즐瑚\uD866\uDDE4\uD81C\uDD75\uD851\uDEE2\uD857\uDF15\uD85F\uDCDA⚋뾸婕阍䩖뻼\uD86F\uDC05ᘣ塬ۧ샻座륐\uD87A\uDC03喚㴵\uD83E\uDD94\uD877\uDF6F\uD83C\uDFF6\uD873\uDD36\uD85F\uDD54\uD870" +
            "\uDC27\uD875\uDF46銽\uD875\uDFE4\uD844\uDC63\uD820\uDDAE뛝\uD803\uDCCD\uD864\uDE92ḏ\uD808\uDC6E\uD86F\uDD9F\uD873\uDDB4\uD858\uDD5A쥭\uD849\uDF82ⅇ賜\uD882\uDE11\uD867\uDE3F\uD884\uDEB8" +
            "\uD883\uDF55\uD878\uDF7D\uD86E\uDCC0\uD856\uDFBA\uD808\uDED1\uD879\uDD96⳱\uD820\uDE83ડ뷉\uD80C\uDCC9菋\uD86C\uDFF1ኌ뇑\uD880\uDDBB\uD867\uDCF3\uD85D\uDE48\uD848\uDD59\uD849\uDE43\uD82C" +
            "\uDD08ꪨ\uD87E\uDD79\uD83D\uDCAA\uD880\uDCA2\uD822\uDD7C㯤쟥橮ﾭ萞\uD881\uDC63\uD84E\uDCF4\uD822\uDD08\uD81C\uDC53\uD854\uDC86\uD840\uDDEB\uD840\uDDF5\uD872\uDFE9Ⳟ됰\uD81D\uDC03\uD85C" +
            "\uDC3A을笆㤷\uD83B\uDE37\uD86B\uDE51\uD811\uDE04ἇ瞡\uD884\uDCB7矺\uD870\uDD04ؗ\uD842\uDDBC쳬\uD884\uDE34ᰳ\uD820\uDC8Fૡ\uD85B\uDC97蜦\uD882\uDE3B\uD804\uDED3\uD855\uDD19\uD853\uDD44\uD86A" +
            "\uDFFF\uD81F\uDEA6\uD81E\uDCE5昝\uD81C\uDF4D壆Ꙑᘛ\uD848\uDC5B\uD85E\uDCA5璐◓\uD883\uDDDCꈛ茝\uD870\uDF36䄊猒\uD84E\uDD39\uD85D\uDE1B\uD84A\uDC31\uD85A\uDF14ﯷ\uD800\uDFCF\uD86B\uDF45겺쐷" +
            "\uD858\uDDF5\uD80C\uDED3\uD863\uDD4A\uD872\uDF1E\uD81A\uDEF0\uD84D\uDCCB\uD849\uDEE5\uD803\uDFEE\uD84B\uDFE1\uD859\uDE2D\uD860\uDDD0믜罹ꅒ\uD871\uDDE4橨\uD866\uDD63რ밆\uD873\uDD31\uD868" +
            "\uDCAD\uD800\uDE80ᘐ\uD847\uDFAF\uD845\uDF97\uD841\uDD02\uD870\uDF65\uDB40\uDD82\uD85F\uDD88\uD820\uDFE8\uD847\uDDC4௧\uD848\uDE28\uD84D\uDF4E\uD852\uDC7B\uD805\uDE00䲮彝\uD875\uDF5A쌴" +
            "\uD852\uDF76岫\uD869\uDEC0\uD847\uDC5B謆䩯\uD86A\uDEFB엎\uD873\uDC85\uD87A\uDC29\uD87A\uDDD0翳\uD855\uDF7D\uD86B\uDC07챃\uD80C\uDC9E\uD86E\uDC68堁烜ᵍ疯型珱\uD884\uDCF3ខ쁁\uD802\uDC75䪆欒迤农玮" +
            "\uD822\uDE30ᯬ\uD857\uDD6D\uD805\uDC14\uD848\uDDE4\uD853\uDD29秸\uD882\uDED9\uD85E\uDF95ᒏ\uD847\uDD69퇡\uD870\uDE30\uD86A\uDDD6↟뙕\uD834\uDDD6\uD876\uDDDA퓌\uD860\uDD56\uD846\uDE2A\uD81F" +
            "\uDD37\uD883\uDDB0ඣ\uD866\uDCA1ꡝ哋\uD83E\uDDA4\uD85B\uDD13刺縓볳\uD834\uDD46\uD822\uDF82\uD809\uDCDE\uD857\uDF4C\uD81C\uDC23\uD86D\uDCE7\uD80C\uDE0C\uD847\uDD8B\uD847\uDC6A\uD822\uDDFA쥡旇" +
            "\uD86C\uDFF0\uD820\uDE14\uD87A\uDF9A\uD843\uDF60\uD84A\uDE6D쏙.\uD877\uDE46暔\uD846\uDE79蝕솵翑\uD883\uDEFB\uD85F\uDCB8\uD853\uDEE2肻\uD85F\uDC13ᎀ\uD871\uDE9F\uD850\uDFE3\uD840\uDD94륰\uD823" +
            "\uDC50\uD800\uDF6C\uD877\uDF21\uD850\uDC02\uD866\uDC2D\uD876\uDEA5킺\uD844\uDD2E\uD823\uDC45鴘࠳\uD85A\uDF44蝂䢞晒\uD858\uDCC7\uD81D\uDC1F\uD878\uDF2F\uD874\uDE00\uD869\uDD49\uD809\uDC86팥" +
            "\uD852\uDD14媂郢\uD811\uDC1C\uD84E\uDD96\uD86D\uDC7C\uD84E\uDC1E\uDB40\uDC2A\uD805\uDCD3\uD85A\uDD58\uD859\uDD1B\uD860\uDEB8\uD86F\uDE52㖀櫊궋\uD853\uDCB0璅Ꭸ\uD872\uDE6C\uD806\uDE36\uD884" +
            "\uDCF3\uD856\uDF20ﬢ疪胥\uD859\uDCB9退\uD809\uDCF7\uD880\uDDC3\uD841\uDE32\uD879\uDC88崍ﰷ\uD85F\uDEDDꄸ\uD848\uDD63戫\uD835\uDF5A\uD84C\uDF3F\uD863\uDEC0ᬠ\uD872\uDD16\uD848\uDDAD㨢㣟\uD836" +
            "\uDC03\uD84A\uDE4B\uD84D\uDDEE\uD821\uDFA7⑴瀾⌃\uD801\uDD30鲀櫳\uD875\uDC9D\uD81F\uDE1F滽⺦\uD882\uDEF8\uD880\uDCFD俾\uD868\uDE0E鐿龂竀偔\uD840\uDC43䄛\uD861\uDE2A擷\uD859\uDF2E繰\uD81C\uDF00" +
            "\uD873\uDF31瓘开\uD84A\uDEBC珹Ժ嫞쀁ಽ䎿\uD81F\uDD44쮃\uD850\uDE19\uD850\uDC02祗鮬\uD878\uDE6C\uD855\uDF54\uD820\uDD36\uD84E\uDD10䲔\uD867\uDC5A吣瞱떹\uD876\uDE29\uD84B\uDE58ꍝ宩\uD864\uDE49㈖\uD86D" +
            "\uDF04ｾ\uD80C\uDC33\uD849\uDFDD姮쯋\uD808\uDF2D゚\uD881\uDFBBÝ\uD87E\uDD47誾膺뽮餥\uD800\uDDF2\uD85F\uDC3F㟲᠂⢑\uD86E\uDF2D\uD835\uDF7D闊\uD835\uDEC3嬤\uD84B\uDCA3劻飜伐\uD883\uDC57\uD881\uDD18" +
            "\uD870\uDFBC\uD858\uDE7D\uD807\uDFEBᡨ\uD86C\uDE32⨪鍔\uD81F\uDFBF\uD85F\uDC4A\uD872\uDC7C뜕\uD85F\uDEE7踋\uD851\uDC1D\uD84D\uDFC0\uD82C\uDCCA\uD803\uDD1A譿\uD864\uDFA6\uD853\uDEF0\uD846" +
            "\uDC4E䢟\uD85A\uDF77\uD85F\uDD63\uD868\uDED4\uD86D\uDEC6룐\uD822\uDCEA\uD807\uDFCB\uD82C\uDC7C\uD83E\uDD2B";

    @Getter
    private static final List<String> singleCodePointEmojis = Arrays.asList("\uD83E\uDD76", "\uD83D\uDC80", "\uD83D\uDC7B", "\uD83D\uDC7E");

    @Getter
    private static final List<String> multiCodePointEmojis = Arrays.asList("\uD83D\uDC69\uD83C\uDFFE", "\uD83D\uDC68\u200D\uD83C\uDFED️", "\uD83D\uDC69\u200D\uD83D\uDE80");

    @Getter
    private static final List<String> abugidasChars = List.of("జ్ఞ\u200Cా", "স্র\u200Cু");

    private static final List<String> ZW_CHARS_SMALL_LIST = List.of("\u200B", "\u200C", "\u200D", "\u200E", "\u200F",
            "\u202A", "\u202B", "\u202C", "\u202D", "\u202E", "\u202F");

    private static final List<String> INVALID_JSONS = List.of("{0}", "{0.0}", "[{}]", "{$}", "[]", "{}",
            """ 
                    {"circularRef": {"self": {"$ref": "#/circularRef"}}} \
                    """,
            """
                    {"backslash": "\\"} \
                    """,
            """
                    {"ünicode": "ünicode"} \
                    """,
            """
                    "{"unexpected" $ "token": "value"} \
                    """,
            """
                    {\u0000:\u0000} \
                    """,
            """
                    {"\u0000":"\u0000"} \
                    """,
            """
                    {"␀":"␀"} \
                    """,
            """
                    {␀:␀} \
                    """);

    private UnicodeGenerator() {
        //ntd
    }

    /**
     * Returns a list of payloads of max(6, size) size.
     *
     * @param size the size of the current data
     * @return a list of payloads to be used for fuzzing
     */
    public static List<String> getAllPayloadsOfSize(int size) {
        String finalSize = String.valueOf(Math.min(size, 6));
        List<String> payloads = new ArrayList<>();
        List<String> result = new ArrayList<>();
        payloads.addAll(getAbugidasChars());
        payloads.addAll(getControlCharsFields());
        payloads.addAll(getControlCharsHeaders());
        payloads.addAll(getSeparatorsFields());
        payloads.addAll(getSeparatorsHeaders());
        payloads.addAll(getSpacesHeaders());
        payloads.addAll(getSingleCodePointEmojis());
        payloads.addAll(getMultiCodePointEmojis());

        String allChars = String.join("", payloads);
        Matcher matcher = Pattern.compile(".{1," + finalSize + "}", Pattern.DOTALL).matcher(allChars);
        while (matcher.find()) {
            result.add(allChars.substring(matcher.start(), matcher.end()));
        }

        return result;
    }

    /**
     * Gets a list of payload with unicode separators for field fuzzing.
     *
     * @return A list of payloads with unicode separators to be used for field fuzzing.
     */
    public static List<String> getSeparatorsFields() {
        return whitespacesFields;
    }

    /**
     * Gets a list of payload with unicode separators for headers fuzzing.
     *
     * @return A list of payloads with unicode separators to be used for headers fuzzing.
     */
    public static List<String> getSeparatorsHeaders() {
        return whitespacesHeaders;
    }

    /**
     * A payload with specific unicode characters.
     *
     * @return A payload with specific unicode characters.
     */
    public static String getBadPayload() {
        return BAD_PAYLOAD;
    }

    /**
     * Gets a list of payload with invalid references for field fuzzing.
     *
     * @return A list of payloads with invalid references to be used for field fuzzing.
     */
    public static List<String> getInvalidReferences() {
        return INVALID_REFERENCES;
    }

    /**
     * Gets the Zalgo text without spaces.
     *
     * @return The Zalgo text without spaces.
     */
    public static String getZalgoText() {
        return ZALGO_TEXT.replace(" ", "");
    }

    /**
     * Returns a list with invalid json payloads.
     *
     * @return A list with invalid json payloads
     */
    public static List<String> getInvalidJsons() {
        return INVALID_JSONS;
    }

    /**
     * Gets a list of zero width characters.
     *
     * @return A list of zero width characters.
     */
    public static List<String> getZwCharsSmallList() {
        return ZW_CHARS_SMALL_LIST;
    }

    /**
     * Generates a random unicode string of given length matching the given predicate.
     *
     * @param length    the length of the string to be generated
     * @param predicate the predicate to test when generating chars
     * @return a string of given length with all chars passing given predicate
     */
    public static String generateRandomUnicodeString(int length, Predicate<Character> predicate) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char randomChar;
            do {
                // Generate a random char value
                randomChar = (char) CatsUtil.random().nextInt(Character.MAX_VALUE);
            } while (!predicate.test(randomChar));
            stringBuilder.append(randomChar);
        }
        return stringBuilder.toString();
    }
}
