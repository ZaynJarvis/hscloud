package com.zaynjarvis.hscloud.controller;

import java.util.List;

public class Display {
    public static String validate(List<Item> items) {
        for (Item i : items) {
            if (i.duration <= 0) {
                return "invalid duration";
            }
            if (i.content.size() > 5) {
                return "cannot display rows more than 5";
            }
            for (List<String> r : i.content) {
                if (r.size() > 3) {
                    return "cannot display columns more than 3";
                }
                for (String txt : r) {
                    if (txt.length() > 100) {
                        return "each text length should not be larger than 100";
                    }
                }
            }
        }
        return "";
    }
    public static String validatePortrait(List<Portrait> items) {
        for (Item i : items) {
            if (i.duration <= 0) {
                return "invalid duration";
            }
            if (i.content.size() > 5) {
                return "cannot display rows more than 5";
            }
            for (List<String> r : i.content) {
                if (r.size() > 3) {
                    return "cannot display columns more than 3";
                }
                for (String txt : r) {
                    if (txt.length() > 100) {
                        return "each text length should not be larger than 100";
                    }
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

    static class EX {
        private String text;
        private String background;
        private String color;

        public String getText() {
            if (null == text) {
                return "";
            }
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getBackground() {
            if (null == background || background.isEmpty()) {
                return "186F65";
            }
            return background;
        }

        public void setBackground(String background) {
            this.background = background;
        }

        public String getColor() {
            if (null == color || color.isEmpty()) {
                return "000000";
            }
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }

    public static class Portrait extends Item {

        private boolean is_en;
        private EX header;
        private EX footer;

        public boolean isIs_en() {
            return is_en;
        }

        public void setIs_en(boolean is_en) {
            this.is_en = is_en;
        }

        public EX getHeader() {
            return header;
        }

        public void setHeader(EX header) {
            this.header = header;
        }

        public EX getFooter() {
            return footer;
        }

        public void setFooter(EX footer) {
            this.footer = footer;
        }


    }

}

