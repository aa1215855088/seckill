package com.xzl.seckill;

import cn.hutool.http.HttpUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * seckill应用程序
 *
 * @author xuzilou
 * @date 2021/01/06
 */
@SpringBootApplication
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
        String html = HttpUtil.get("https://item.jd.com/100012043978.html");
        Document doc = Jsoup.parse(html);
        System.out.println(doc.getElementsByClass("p-name").text());
    }

}
