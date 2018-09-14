package com.rey.swagger;

import v2.io.swagger.models.Swagger;
import v2.io.swagger.parser.SwaggerParser;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

public class App {

    public static void main(String[] args) {
        try {
            if(args.length != 2)
                System.out.println("Incorrect arguments");
            else if(!isFile(args[0]))
                System.out.println("File not found: " + args[0]);
            else if(!isFile(args[1]))
                System.out.println("File not found: " + args[1]);
            else {
                SwaggerParser parser = new SwaggerParser();
                final Swagger expected = parser.read(args[0]);
                final Swagger actual = parser.read(args[1]);
                SwaggerComparator swaggerComparator = new SwaggerComparator(expected, actual);
                System.out.println(swaggerComparator.compare());
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.out.println(sw.toString());
        }
    }

    private static boolean isFile(String path) {
        File file = new File(path);
        return file.exists() && !file.isDirectory();
    }

}
