package dynamicparsestarter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.dynamicparse.decompile.Decompile;
import com.hayden.dynamicparse.decompile.DecompilePrinter;
import com.hayden.dynamicparse.decompile.LoadClass;
import com.hayden.dynamicparse.parse.DynamicParseJson;
import com.hayden.dynamicparse.parse.DynamicParsingException;
import com.hayden.dynamicparse.parse.ReParse;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * must use illegal-access=permit and also because you can only create the class once you have to run
 * these tests one by one for them to pass
 */
@SpringBootTest(classes={DynamicParseJson.class, ObjectMapper.class, DecompilePrinter.class, LoadClass.class, ReParse.class, Decompile.class})
@ExtendWith(SpringExtension.class)
class DynamicParseStarterApplicationTests {

    @Autowired
    DynamicParseJson dynamicParseJson;
    @Autowired
    ReParse reParse;
    @Autowired
    Decompile decompile;
    @Autowired
    ObjectMapper om;

    @Test @SneakyThrows
    public void symbols(){
        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/symbol.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        dynamicParseJson.dynamicParse(sb.toString(), "symbol", Optional.empty(), Optional.of("src/main/java/com/hayden/dynamicparsestarter/dynamic")).get();

//        var symbols = dynamicParseJson.parseParsedByKey("symbol", output, obj);
//        assertThat(symbols.size()).isEqualTo(121);
    }

    /**
     * doesn't pass because of the order but actually does what it wants to do
     */
    @Test
    @SneakyThrows
    public void mostComplexGettingValues(){
        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/test.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "token", Optional.empty(), Optional.empty()).get();
        var obj = om.readValue(sb.toString(), output.clzz().toClass());

        var timeInFoce = reParse.parseParsedByKey("timeInForce", output, obj);
        for(var t : timeInFoce){
            System.out.println(t);
        }
    }

    @Test @SneakyThrows
    public void testDecompile(){

        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/test_1.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var c  = dynamicParseJson.dynamicParse(sb.toString(), "token_1", Optional.empty(), Optional.of("src/main/resources")).get();

        c.clzz().freeze();
        c.clzz().toClass();

        c.clzz().writeFile();
        c.clzz().writeFile("src/main/resources");

        assertThat(new File("src/main/resources/token_1.class").exists()).isTrue();

        var name = Class.forName("token_1").getName();

        var decompiled = decompile.decompile(name);

        try {
            assertThat(decompiled).isInstanceOf(String.class);
            assertThat(decompiled.length()).isNotZero();
            System.out.println("decompiled: \n\n" + decompiled);
        } catch (AssertionError a){
            assertThat(decompiled).isInstanceOf(String.class);
            assertThat(decompiled.length()).isNotZero();
            System.out.println("decompiled: \n\n" + decompiled);
        }
    }

    @Test @SneakyThrows
    public void mostComplex(){
        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/test.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "token_2", Optional.empty(), Optional.empty()).get();
        System.out.println(output);
        var val = om.readValue(sb.toString(), output.clzz().toClass());
        assertThat(om.writeValueAsString(val)).isEqualTo(sb.toString().replaceAll("\\s+", ""));
    }

    @Test @SneakyThrows
    public void testParseParsed(){
        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/toParse.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "TestParsed", Optional.empty(), Optional.of("src/main/resources")).get();
        var val = om.readValue(sb.toString(), output.clzz().toClass());

        List<Object> bids = reParse.parseParsedByKey("bids", output, val);

        System.out.println(bids);

        assertThat(bids.get(0)).isInstanceOf(String[][].class);

        var strs = ((String[][]) bids.get(0));
        System.out.println(Arrays.deepToString(strs));
    }

    @Test @SneakyThrows
    public void testParseSaveClass(){

        File file = new File("src/main/java/com/hayden/dynamicparsestarter/dynamic/"+"TestParse_1.java");
        if(file.exists()){
            file.delete();
        }

        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/toParse_1.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "TestParse", Optional.empty(), Optional.empty()).get();
        output.clzz().writeFile("src/main/java/com/hayden/dynamicparsestarter/dynamic");
        file.deleteOnExit();

    }

    @Test
    public void testParseLoadClass() throws CannotCompileException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException, IOException, NoSuchFieldException, DynamicParsingException {
        StringBuilder sb = new StringBuilder();
        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/toParse.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "TestParsed_1", Optional.empty(), Optional.empty()).get();
        Class testParsedClass = output.clzz().toClass();

        System.out.println(testParsedClass);

        var o = testParsedClass.getConstructor(null).newInstance();
        System.out.println(o.getClass().getName());

        o.getClass().getField("lastUpdateId").set(o, 4321L);
        assertThat(om.writeValueAsString(o)).isEqualTo("{\"lastUpdateId\":4321,\"T\":null,\"E\":null,\"asks\":null,\"bids\":null}");
    }

    @Test
    public void testParseLoadClassWithCollectionOfInnerObjects() throws IOException, CannotCompileException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException, DynamicParsingException {
        StringBuilder sb = new StringBuilder();
        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/innerObjParse.json"))){
            fr.lines().forEachOrdered(sb::append);
        }
        var output = dynamicParseJson.dynamicParse(sb.toString(), "TestParseComplex", Optional.empty(), Optional.empty()).get();
        System.out.println(output);
        var complex = output.clzz().toClass().getConstructor(null).newInstance();
        var readVal = om.readValue(sb.toString(), Class.forName(complex.getClass().getName()));
        System.out.println(om.writeValueAsString(readVal));
    }

    @Test
    public void testParseObjArr() throws Exception, DynamicParsingException {
        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/objArrParse.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output = dynamicParseJson.dynamicParse(sb.toString(), "TestParseObjArr", Optional.empty(), Optional.empty()).get();
        System.out.println(output);

        output.arrClzz().flatMap(clzz -> {
            try {
                var readVal = om.readValue(sb.toString(), clzz);
                System.out.println(om.writeValueAsString(readVal));
                assertThat(om.writeValueAsString(readVal)).isEqualTo(sb.toString().replaceAll("\\s+", ""));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        });

    }

}
