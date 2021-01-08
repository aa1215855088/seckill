package com.xzl.seckill;


import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

/**
 * <p>
 * 启动类
 * </p>
 *
 * @author xzl
 * @date 2021/1/6
 */
public class Main {

    private static final Log log = LogFactory.get();


    public static void main(String[] args) {
        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>start<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        Seckill seckill = new Seckill();
    }
}
