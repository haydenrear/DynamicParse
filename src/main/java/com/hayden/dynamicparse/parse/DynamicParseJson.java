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
    private Class<?> mapArray;
    private Class<?> clzzFound;

    public DynamicParseJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record CtClzzz(CtClass clzz, Optional<Class<?>> arrClzz, List<CtClass> ctClzzzs){}

    public List<Object> parseParsedByKey(String key, CtClzzz clzzToParse, Object dataToParse) {
        Object obj = null;
        Object objFound = null;

        List<Object> values = new ArrayList<>();

        try {
            clzzFound = clzzToParse.arrClzz.orElse(Class.forName(clzzToParse.clzz.getName()));
            for (Field f : clzzFound.getFields()) {
                if (f.getName().equals(key)) {
                    obj = f.get(dataToParse);
                    values.add(obj);
                }
                else {
                    values.addAll(parseParsedByKey(key, f.getType(), f.get(dataToParse)));
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return values;
    }


    public List<Object> parseParsedByKey(String key, Class<?> clzzToParse, Object dataToParse) {
        List<Object> lst = new ArrayList<>();

        try {
            Class<?> arrType = clzzToParse;

            while(clzzToParse.isArray()){
                clzzToParse = clzzToParse.getComponentType();
            }

            if(clzzToParse == Map.class){
                if(clzzToParse != arrType){
                    List<Object> maps = new ArrayList<>();
                    iterateAndAddToList(dataToParse, maps);
                    for(var map : maps){
                        iterateMap(key, (Map) map);
                    }
                }
                else{
                    iterateMap(key, (Map) dataToParse);
                }
            }

            else if(!ClassUtils.isPrimitiveOrWrapper(clzzToParse) && clzzToParse != String.class) {
                for (Field f : clzzToParse.getFields()) {

                    if (f.getName().equals(key)) {
                        List<Object> tempList = new ArrayList<>();
                        iterateAndAddToList(dataToParse, tempList);
                        for(var o : tempList){
                            lst.add(f.get(o));
                        }
                    }

//                else {
                    else if (!ClassUtils.isPrimitiveOrWrapper(f.getType()) && f.getType() != String.class) {
                        List<Object> upNext = new ArrayList<>();
                        iterateAndAddToList(dataToParse, upNext);
                        for(var o : upNext){
                            lst.addAll(parseParsedByKey(key, f.getType(), f.get(o)));
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return lst;
    }

    private List<Object> iterateMap(String key, Map map) throws IllegalAccessException {
        List<Object> lst = new ArrayList<>();
        for(var entry : map.entrySet()){
            if(entry instanceof Map.Entry<?,?> e){
                if(e.getKey() instanceof String keyVal){
                    if(keyVal.equals(key)){
                        if(ClassUtils.isPrimitiveOrWrapper(e.getValue().getClass())){
                            lst.add(e.getValue());
                        }
                        else {
                            iterateAndAddToList(e.getValue(), lst);
                        }
                    }
                }
            }
        }
        return lst;
    }


    public void iterateAndAddToList(Object dataToParse, List<Object> lst) throws IllegalAccessException {
        List<Object> objectsTo = new ArrayList<>();
        var finalVals = tryArrayAndAdd(dataToParse, objectsTo);
        lst.addAll(finalVals);
    }

    public List<Object> tryArrayAndAdd(Object toCheck, List<Object> objects){
        List<Object> tempList = new ArrayList<>();
        if(toCheck instanceof Object[] arr){
            if(objects.size() >1){
                for(var o : objects){
                    tempList.addAll(Arrays.asList((Object[]) o));
                }
                tempList.addAll(tryArrayAndAdd(tempList.get(0), tempList));
            }
            else {
                tempList.addAll(Arrays.asList((Object[]) toCheck));
            }
        }
        return tempList;
    }

    public record ClassAndClasses(CtClass clzz, List<CtClass> innerClzzs){}

    public ClassAndClasses clzzFound(CtClzzz toLookThrough, String key) throws NotFoundException {
        var lst = new ArrayList<CtClass>();
        for(var ctClzz : toLookThrough.ctClzzzs()){
            if(ctClzz.getName().contains(key)){
                lst.add(ctClzz);
                return new ClassAndClasses(toLookThrough.clzz, lst);
            }
            else {
                lst.add(ctClzz);
                var searched = clzzFound(Arrays.stream(ctClzz.getNestedClasses()).toList(), key, lst);
                if(searched != null){
                    return searched;
                }
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
        if(arr.size() == 0){
            try {
                addFieldToCtClass(newClass, prev, ClassPool.getDefault().get(Array.newInstance(String.class,0).getClass().getName()));
            } catch (NotFoundException e) {
                e.printStackTrace();
                System.out.println("unable to add field for value with no values");
            }
        }
        else {
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
                    if(justAMap(arr)){
//                        if(primitiveValues(arr)){
                            try {
                                mapArray = Array.newInstance(Map.class, 0).getClass();
                                addFieldToCtClass(newClass, prev, ClassPool.getDefault().get(mapArray.getName()));
                                return new CtClzzz(newClass, Optional.of(mapArray), Arrays.stream(newClass.getNestedClasses()).toList());
                            } catch (NotFoundException e) {
                                e.printStackTrace();
                            }
                            //Todo: Need to account for map of something other than primitive ... such as String to one object
//                        }
//                        else {
//                            var val =  object.values().stream().findFirst();
//                            if(val.get() instanceof JSONObject ob){
//
//                            }
//                            else if (val.get() instanceof JSONArray ar){
//                                dynamicParse(arr.toJSONString(), )
//                            }
//                        }
                    }
                    else {
                        try {
                            var innerDynamic = dynamicParse(object.toJSONString(), prev, Optional.of(newClass.makeNestedClass(prev, true)), directoryName);
                            Class<?> clzz = null;
                            try {
                                clzz = Array.newInstance(Class.forName(innerDynamic.get().clzz.getName()), 1).getClass();
                            } catch (ClassNotFoundException e) {
                                clzz = Array.newInstance(innerDynamic.get().clzz.toClass(), 1).getClass();
                            }

                            addFieldToCtClass(newClass, prev, ClassPool.getDefault().get(clzz.getName()));
                            objectMapper.registerSubtypes(Class.forName(innerDynamic.get().clzz.getName()), clzz);
                            return new CtClzzz(newClass, Optional.of(clzz), Arrays.stream(newClass.getNestedClasses()).toList());

                        } catch (NotFoundException | CannotCompileException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
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
                            var innerClass = dynamicParse(primOr.jo.toJSONString(), prev, Optional.of(ClassPool.getDefault().makeClass(prev)), directoryName);
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

        }
        return null;
    }

    private boolean primitiveValues(JSONArray arr) {
        for(var a : arr) {
            if (a instanceof JSONObject obj) {
                for(var v : obj.values()){
                    if(!ClassUtils.isPrimitiveOrWrapper(v.getClass()))
                        return false;
                }
            }
        }
        return true;
    }

    private boolean justAMap(JSONArray arr) {
        Set<String> prev = null;
        for(var a : arr) {
            if (a instanceof JSONObject obj) {
                if (obj.keySet().stream().findFirst().get() instanceof String) {
                    if (prev == null)
                        prev = obj.keySet();
                    else return !prev.containsAll((Set<String>) obj.keySet());
                }
            }
        }
        return true;
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
                        var innerDynamic = dynamicParse(object.toJSONString(), key, Optional.of(ClassPool.getDefault().makeClass(key)), directoryName);
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
