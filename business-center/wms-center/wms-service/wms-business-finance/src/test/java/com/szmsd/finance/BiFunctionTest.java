package com.szmsd.finance;

import com.alibaba.fastjson.JSON;
import org.apache.poi.ss.formula.functions.T;

import java.util.function.BiFunction;
import java.util.function.Function;

public class BiFunctionTest {

    private Integer compute(Integer num, Function<Integer,Integer> function){
        return function.apply(num);
    }

    private Tsum computeForBiFunction(Integer num1, Integer num2,BiFunction<Integer, Integer, Tsum> biFunction) {
        return biFunction.apply(num1, num2);
    }

    private Tsum st(Integer v1,Integer v2){

        Tsum tsum = new Tsum();

        tsum.setV(v1);
        tsum.setB(v2);

        return tsum;
    }

    private Tsum result(Integer num1, Integer num2){
        return computeForBiFunction(num1,num2,(v1, v2) -> st(v1,v2));
    }


    public static void main(String[] args) {

        BiFunctionTest biFunctionTest = new BiFunctionTest();
        Integer addResult = biFunctionTest.compute(3,v -> v+v);
        System.out.println(addResult);

        Tsum addRs = biFunctionTest.result(2,3);
        System.out.println(JSON.toJSONString(addRs));

    }
}
