package com.hayden.dynamicparse.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import javassist.*;
import org.apache.commons.lang3.ClassUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class DynamicParseJson {

    ObjectMapper objectMapper;

    public DynamicParseJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record CtClzzz(CtClass clzz, Optional<Class<?>> arrClzz, List<CtClass> ctClzzzs){}

    public List<Object> parseParsedByKey(String key, CtClzzz clzzToParse, Object dataToParse) throws NotFoundException {
        Object obj = null;

        List<Object> values = new ArrayList<>();
        List<CtClass> clzzes = clzzFound(clzzToParse, key).innerClzzs;
        clzzes.add(clzzToParse.clzz);

        for (CtClass ctClass : clzzes) {
            try {
                for (Field f : Class.forName(ctClass.getName()).getFields()) {
                    if (f.getName().equals(key)) {
                        obj = f.get(obj == null ? dataToParse : obj);
                        values.add(obj);
                    }
                }
            } catch (ClassNotFoundException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return values;
    }

    public record ClassAndClasses(CtClass clzz, List<CtClass> innerClzzs){}

    public ClassAndClasses clzzFound(CtClzzz toLookThrough, String key) throws NotFoundException {
        var lst = new ArrayList<CtClass>();
        for(var ctClzz : toLookThrough.ctClzzzs()){
            if(ctClzz.getName().contains(key)){
                return new ClassAndClasses(toLookThrough.clzz, List.of(ctClzz));
            }
            else {
                lst.add(ctClzz);
                return clzzFound(Arrays.stream(ctClzz.getNestedClasses()).toList(), key, lst);
            }
        }
        return new ClassAndClasses(toLookThrough.clzz, new ArrayList<>());
    }

    public ClassAndClasses clzzFound(List<CtClass> toLookThrough,
                                     String key,
                                     List<CtClass> parents
    ) throws NotFoundException {
        for(var ctClzz : toLookThrough){
            if(ctClzz.getName().contains(key)){
                return new ClassAndClasses(ctClzz, parents);
            }
            else {
                var newList = new ArrayList<>(parents);
                newList.add(ctClzz);
                return clzzFound(Arrays.stream(ctClzz.getNestedClasses()).toList(), key, newList);
            }
        }
        return null;
    }

    public Optional<CtClzzz> dynamicParse(String data, String name, Optional<String> directoryName) throws DynamicParsingException {
        return dynamicParse(data, name, Optional.empty(), directoryName);
    }

    public Optional<CtClzzz> dynamicParse(String data, String name) throws DynamicParsingException {
        return dynamicParse(data, name, Optional.empty(), Optional.empty());
    }

    public Optional<CtClzzz> dynamicParse(String data, CtClass clzz, String name) throws DynamicParsingException {
        return dynamicParse(data, name, Optional.of(clzz), Optional.empty());
    }

    public Optional<CtClzzz> dynamicParse(String data, String name, Optional<CtClass> parentClass, Optional<String> directoryName) throws DynamicParsingException {

        CtClass newClass = parentClass.orElseGet(() -> ClassPool.getDefault().makeClass(name));

        newClass.setModifiers(Modifier.PUBLIC);
        try {
            newClass.addInterface(ClassPool.getDefault().get("java.io.Serializable"));
        } catch (NotFoundException e) {
            e.printStackTrace();
        }

        try {
            var jsonParser = new JSONParser();
            Object parsed = jsonParser.parse(data);
            if(parsed instanceof JSONObject obj){
                dynamicParse(obj, newClass, directoryName);
            }
            else if(parsed instanceof JSONArray arr){
                return Optional.of(dynamicParse(arr, newClass, name+"List", directoryName));
            }
            else {
                throw new DynamicParsingException("Object parsed neither object nor array");
            }
            if(directoryName.isPresent()){
                newClass.writeFile(directoryName.get());
            }
            return Optional.of(new CtClzzz(newClass, Optional.empty(), Arrays.stream(newClass.getNestedClasses()).toList()));
        } catch (ParseException | NotFoundException | CannotCompileException | IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();

    }


    public CtClzzz dynamicParse(
            JSONArray arr,
            CtClass newClass,
            String prev,
            Optional<String> directoryName
    ) throws DynamicParsingException {
        for(var toParse : arr){
            var typeOfArray = Array.newInstance(toParse.getClass(), 0).getClass();
            if(ClassUtils.isPrimitiveOrWrapper(toParse.getClass()) || toParse instanceof String){
                try {
                    var ctArray = ClassPool.getDefault().get(typeOfArray.getName());
                    addFieldToCtClass(newClass, prev, ctArray);
                    break;
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }
            }
            else if(toParse instanceof JSONObject object){
                try {
                    var innerDynamic = dynamicParse(object.toJSONString(), prev, Optional.of(newClass.makeNestedClass(prev, true)), directoryName);
                    Class<?> clzz = null;
                    try {
                        clzz = Class.forName(innerDynamic.get().clzz.getName());
                    } catch (ClassNotFoundException e) {
                        clzz = innerDynamic.get().clzz.toClass();
                    }
                    addFieldToCtClass(newClass, prev, innerDynamic.get().clzz);
                    objectMapper.registerSubtypes(clzz);
                    return new CtClzzz(newClass, Optional.of(clzz), Arrays.stream(newClass.getNestedClasses()).toList());
                } catch (NotFoundException | CannotCompileException e) {
                    e.printStackTrace();
                }
                break;
            }
            else if(toParse instanceof JSONArray innerArr){
                var primOrOpt = findIfPrimitive(innerArr, 1);
                if(primOrOpt.isPresent()) {
                    var primOr = primOrOpt.get();
                    if(primOr.isPrim){
                        try {
                            CtClass arrCtClzz = ClassPool.getDefault().get(Array.newInstance(primOr.primType, primOr.depth, 1).getClass().getName());
                            addFieldToCtClass(newClass, prev, arrCtClzz);
                            return new CtClzzz(newClass, Optional.of(primOr.primType()), Arrays.stream(newClass.getNestedClasses()).toList());
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        var innerClass = dynamicParse(primOr.jo.toJSONString(), prev, Optional.of(newClass.makeNestedClass(prev, true)), directoryName);
                        if(innerClass.isPresent()){
                            try {
                                var clzz = innerClass.get().clzz.toClass();
                                objectMapper.registerSubtypes(clzz);
                                var arrCtClzz = ClassPool.getDefault().get(Array.newInstance(clzz, primOr.depth, 1).getClass().getName());
                                addFieldToCtClass(newClass, prev, arrCtClzz);
                                return new CtClzzz(newClass, Optional.of(clzz), Arrays.stream(newClass.getNestedClasses()).toList());
                            } catch (NotFoundException | CannotCompileException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                }
                else throw new DynamicParsingException("Problem recursively finding depth of array ..");
            }
            break;
        }
        return null;
    }

    public void dynamicParse(JSONObject obj,
                             CtClass newClass,
                             Optional<String> directoryName
    )
            throws DynamicParsingException
    {
        for(var toParse : obj.entrySet()){
            if(toParse instanceof Map.Entry entry) {
                if(entry.getKey() instanceof String key){
                    if(ClassUtils.isPrimitiveOrWrapper(entry.getValue().getClass()) || entry.getValue() instanceof String){
                        try {
                            var ctClass = ClassPool.getDefault().get(entry.getValue().getClass().getName());
                            addFieldToCtClass(newClass, key, ctClass);
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    else if(entry.getValue() instanceof JSONObject object){
                        var innerDynamic = dynamicParse(object.toJSONString(), key, Optional.of(newClass.makeNestedClass(key, true)), directoryName);
                        if(innerDynamic.isPresent()) {
                            try {
                                addFieldToCtClass(newClass, key, innerDynamic.get().clzz);
                                objectMapper.registerSubtypes(innerDynamic.get().clzz.toClass());
                            } catch (CannotCompileException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    else if(entry.getValue() instanceof JSONArray arr){
                        dynamicParse(arr, newClass, key, directoryName);
                    }
                }
            }
        }
    }

    public record PrimOrObj(boolean isPrim, int depth, JSONObject jo, Class<?> primType){}

    public Optional<PrimOrObj> findIfPrimitive(JSONArray jsonArray, int depth){
        for(var j : jsonArray){
            if(ClassUtils.isPrimitiveOrWrapper(j.getClass()) || j instanceof String){
                return Optional.of(new PrimOrObj(true, depth, null, j.getClass()));
            }
            else if(j instanceof JSONArray ja){
                return findIfPrimitive(ja, depth+1);
            }
            else if(j instanceof JSONObject jo){
                return Optional.of(new PrimOrObj(false, depth, jo, null));
            }
        }
        return Optional.empty();
    }

    private void addFieldToCtClass(CtClass newClass, String key, CtClass ctClass)  {
        try {
            if(newClass.isFrozen())
                newClass.defrost();
            CtField field = new CtField(ctClass, key, newClass);
            field.setModifiers(Modifier.PUBLIC);
            newClass.addField(field);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }
    }

}
