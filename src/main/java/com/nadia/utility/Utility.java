package com.nadia.utility;

public class Utility {
    public static interface Callback {
        void report(String data);
    }

    public static void downloadFiles(String threads, String outputFolder, String linksFileName) {
    }

    public static void main(String[] args) {
        String threads = "5";
        String outputFolder = "output_folder";
        String linksFileName = "links.txt";
        if (args.length == 0) {
            System.out.println("Usage: java -jar utility.jar 5 output_folder links.txt");
        } else {
            if (args.length >= 1) {
                threads = args[0];
            } else if (args.length >= 2) {
                outputFolder = args[1];
            } else if (args.length >= 3) {
                linksFileName = args[2];
            }
            downloadFiles(threads, outputFolder, linksFileName);
        }
    }
}
