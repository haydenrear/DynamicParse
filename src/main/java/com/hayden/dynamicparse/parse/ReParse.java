package com.hayden.dynamicparse.parse;

import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ClassUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@NoArgsConstructor
public class ReParse {



    @SneakyThrows
    public List<Object> parseParsedByKey(
            String key,
            DynamicParseJson.ClassInfo clzzToParse,
            Object dataToParse
    )
    {
        Object obj = null;
        Object objFound = null;

        List<Object> values = new ArrayList<>();

        try {
            var clzzFound = clzzToParse.arrClzz.orElse(Class.forName(clzzToParse.clzz.getName()));
            for (Field f : clzzFound.getFields()) {
                if (f.getName().equals(key)) {
                    obj = f.get(dataToParse);
                    values.add(obj);
                } else {
                    values.addAll(parseParsedByKey(
                            key,
                            f.getType(),
                            f.get(dataToParse)
                    ));
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return values;
    }


    public List<Object> parseParsedByKey(
            String key,
            Class<?> clzzToParse,
            Object dataToParse
    )
    {
        List<Object> lst = new ArrayList<>();

        try {
            Class<?> arrType = clzzToParse;

            while (clzzToParse.isArray()) {
                clzzToParse = clzzToParse.getComponentType();
            }

            if (clzzToParse == Map.class) {
                if (clzzToParse != arrType) {
                    List<Object> maps = new ArrayList<>();
                    iterateAndAddToList(
                            dataToParse,
                            maps
                    );
                    for (var map : maps) {
                        iterateMap(
                                key,
                                (Map) map
                        );
                    }
                } else {
                    iterateMap(
                            key,
                            (Map) dataToParse
                    );
                }
            } else if (!ClassUtils.isPrimitiveOrWrapper(clzzToParse) && clzzToParse != String.class) {
                for (Field f : clzzToParse.getFields()) {

                    if (f.getName().equals(key)) {
                        List<Object> tempList = new ArrayList<>();
                        iterateAndAddToList(
                                dataToParse,
                                tempList
                        );
                        for (var o : tempList) {
                            lst.add(f.get(o));
                        }
                    } else if (!ClassUtils.isPrimitiveOrWrapper(f.getType()) && f.getType() != String.class) {
                        List<Object> upNext = new ArrayList<>();
                        iterateAndAddToList(
                                dataToParse,
                                upNext
                        );
                        for (var o : upNext) {
                            lst.addAll(parseParsedByKey(
                                    key,
                                    f.getType(),
                                    f.get(o)
                            ));
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return lst;
    }

    private List<Object> iterateMap(
            String key,
            Map map
    ) throws IllegalAccessException
    {
        List<Object> lst = new ArrayList<>();
        for (var entry : map.entrySet()) {
            if (entry instanceof Map.Entry<?, ?>) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) entry;
                if (e.getKey() instanceof String) {
                    String keyVal = (String) e.getKey();
                    if (keyVal.equals(key)) {
                        if (ClassUtils.isPrimitiveOrWrapper(e.getValue().getClass())) {
                            lst.add(e.getValue());
                        } else {
                            iterateAndAddToList(
                                    e.getValue(),
                                    lst
                            );
                        }
                    }
                }
            }
        }
        return lst;
    }


    public void iterateAndAddToList(
            Object dataToParse,
            List<Object> lst
    ) throws IllegalAccessException
    {
        List<Object> objectsTo = new ArrayList<>();
        var finalVals = tryArrayAndAdd(
                dataToParse,
                objectsTo
        );
        lst.addAll(finalVals);
    }

    public List<Object> tryArrayAndAdd(
            Object toCheck,
            List<Object> objects
    )
    {
        List<Object> tempList = new ArrayList<>();
        if (toCheck instanceof Object[]) {
            Object[] arr = (Object[]) toCheck;
            if (objects.size() > 1) {
                for (var o : objects) {
                    tempList.addAll(Arrays.asList((Object[]) o));
                }
                tempList.addAll(tryArrayAndAdd(
                        tempList.get(0),
                        tempList
                ));
            } else {
                tempList.addAll(Arrays.asList((Object[]) toCheck));
            }
        }
        return tempList;
    }

}
