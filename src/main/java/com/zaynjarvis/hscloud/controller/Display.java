package com.zaynjarvis.hscloud.controller;

import java.util.List;

public class Display {
    public static String validate(List<Item> items) {
        for (Item i: items) {
            if (i.duration<=0) {
                return "invalid duration";
            }
            if (i.content.size() > 5) {
                return "cannot display rows more than 5";
            }
            for (List<String> r: i.content) {
                if (r.size() > 3) {
                    return "cannot display columns more than 3";
                }
            }
        }
        return "";
    }

    public static class Item {
        private int duration;
        private String color;
        private List<List<String>> content;

        public String getColor() {
            if (color == null || color.isEmpty()) {
                return "ffffff"; // white
            }
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public int getDuration() {
            return duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }

        public List<List<String>> getContent() {
            return content;
        }

        public void setContent(List<List<String>> content) {
            this.content = content;
        }
    }

}

