package dynamicparsestarter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.dynamicparse.parse.DynamicParseJson;
import com.hayden.dynamicparse.parse.DynamicParsingException;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes={DynamicParseJson.class, ObjectMapper.class})
@ExtendWith(SpringExtension.class)
class DynamicParseStarterApplicationTests {

    @Autowired
    DynamicParseJson dynamicParseJson;
    @Autowired
    ObjectMapper om;

    @Test @SneakyThrows
    public void mostComplexGettingValues(){
        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/test.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "token", Optional.empty(), Optional.empty()).get();
        var obj = om.readValue(sb.toString(), output.clzz().toClass());
//        var symbols = dynamicParseJson.parseParsedByKey("symbol", output, obj);
//        assertThat(symbols.size()).isEqualTo(121);
        var timeInFoce = dynamicParseJson.parseParsedByKey("timeInForce", output, obj);
        for(var t : timeInFoce){
            System.out.println(t);
        }
    }

    @Test @SneakyThrows
    public void mostComplex(){
        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/test.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "token", Optional.empty(), Optional.empty()).get();
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

        var output= dynamicParseJson.dynamicParse(sb.toString(), "TestParsed", Optional.empty(), Optional.empty()).get();
        var val = om.readValue(sb.toString(), output.clzz().toClass());

        List<Object> bids = dynamicParseJson.parseParsedByKey("bids", output, val);

        System.out.println(bids);

        assertThat(bids.get(0)).isInstanceOf(String[][].class);

        var strs = ((String[][]) bids.get(0));
        System.out.println(Arrays.deepToString(strs));
    }

    @Test @SneakyThrows
    public void testParseSaveClass(){

        File file = new File("src/main/java/com/hayden/dynamicparsestarter/dynamic/"+"TestParse.java");
        if(file.exists()){
            file.delete();
        }

        StringBuilder sb = new StringBuilder();

        try(BufferedReader fr = new BufferedReader( new FileReader("src/test/resources/toParse.json"))){
            fr.lines().forEachOrdered(sb::append);
        }

        var output= dynamicParseJson.dynamicParse(sb.toString(), "TestParse", Optional.empty(), Optional.empty()).get();
        output.clzz().writeFile("src/main/java/com/hayden/dynamicparsestarter/dynamic");
        file.deleteOnExit();

    }

    @Test
    public void testParseLoadClass() throws CannotCompileException, NotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException, IOException, NoSuchFieldException, DynamicParsingException {
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
                var readVal = om.readValue(sb.toString(), Array.newInstance(clzz, 1).getClass());
                System.out.println(om.writeValueAsString(readVal));
                assertThat(om.writeValueAsString(readVal)).isEqualTo(sb.toString().replaceAll("\\s+", ""));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        });

    }

}
