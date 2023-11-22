package com.zaynjarvis.hscloud.controller;

import jakarta.annotation.PostConstruct;
import nova.traffic.been.DeviceNowPlayList;
import nova.traffic.been.DeviceType;
import nova.traffic.server.NovaDevice;
import nova.traffic.server.ServerChannel;
import nova.traffic.utils.NovaTrafficServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DisplayController {
    private static final Logger logger = LoggerFactory.getLogger(DisplayController.class);
    private static final int[] align = {0, 2, 1};
    private final ServerChannel serverChannel;

    @Autowired
    public DisplayController(ServerChannel serverChannel) {
        this.serverChannel = serverChannel;
    }

    @PostConstruct
    public void init() {
        serverChannel.setClientConnectListener(new NovaDevice.ConnectListener() {
            @Override
            public void onDisconnect(NovaDevice novaDevice, String ip) {
                System.out.println(ip + " disconnected");
            }

            @Override
            public void onConnected(NovaDevice dv, String ip) {
                System.out.println(ip + " " + dv.getDeviceName() + " connected");
            }
        });

        if (serverChannel.open("0.0.0.0", 9000)) {
            System.out.println("open channel success");
        } else {
            System.out.println("open channel failed");
        }
    }

    @PostMapping("/display/{id}")
    public ResponseEntity<?> createDisplay(@PathVariable String id, @RequestBody List<Display.Item> display, @RequestHeader("Authorization") String auth) throws IOException {
        if (!PasswordUtil.ok(auth)) {
            return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);
        }
        // logic to handle creating a new user
        logger.info(display.toString());
        String err = Display.validate(display);
        if (!err.isEmpty()) {
            return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
        }

        String d = formatDisplay(display);
        String push_err = pushToScreen(d, id);
        if (!push_err.isEmpty()) {
            return new ResponseEntity<>(push_err, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(d, HttpStatus.OK);
    }

    @PostMapping("/portrait/{id}")
    public ResponseEntity<?> createPortrait(@PathVariable String id, @RequestBody List<Display.Portrait> display, @RequestHeader("Authorization") String auth) throws IOException {
        if (!PasswordUtil.ok(auth)) {
            return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);
        }
        String err = Display.validatePortrait(display);
        if (!err.isEmpty()) {
            return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
        }
        // logic to handle creating a new user
        logger.info(display.toString());

        String d = formatPortrait(display);
        String push_err = pushToScreen(d, id);
        if (!push_err.isEmpty()) {
            return new ResponseEntity<>(push_err, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(d, HttpStatus.OK);
    }

    @PostMapping("/raw_display/{id}")
    public String createDisplay(@PathVariable String id, @RequestBody String raw) throws IOException {
        logger.info(raw);
        pushToScreen(raw, id);
        return raw;
    }

    @DeleteMapping("/cleanup/{id}")
    public ResponseEntity<?> cleanUp(@PathVariable String id, @RequestHeader("Authorization") String auth) throws IOException {
        if (!PasswordUtil.ok(auth)) {
            return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);
        }

        NovaDevice dv = serverChannel.getDeviceByName(id);
        if (dv == null || !dv.enable()) {
            return new ResponseEntity<>("device " + id + " not enabled", HttpStatus.BAD_REQUEST);
        }
        NovaTrafficServer ts = dv.obtainTrafficServer();

        String clean = """
                [all]
                items=1
                [item1]
                param=100,1,1,1,0,0,1
                txtext1=0,0,0,64,0,1414,1,2,2,3,0,1,000000,0,1,100,1,,0,0,0,0,0,0
                """;
        int res = ts.sendPlayList(1, clean);
        logger.info(String.format("update clean playlist %d", res));
        for (int i = 1; i <= 10; i++) {
            int i1 = ts.removeLocalUpdate(i);
            logger.info(String.format("remove %d: %d", i, i1));
        }

        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    @GetMapping("/screens")
    public ResponseEntity<?> screens() throws IOException {
        ArrayList<Object> al = new ArrayList<>();
        for (NovaDevice dv : serverChannel.getAllDevices()) {
            if (dv == null || !dv.enable()) {
                continue;
            }
            NovaTrafficServer ts = dv.obtainTrafficServer();
            al.add(ts.getDeviceName());
        }

        return new ResponseEntity<>(al, HttpStatus.OK);
    }

    @GetMapping("/debug/{id}")
    public ResponseEntity<?> debug(@PathVariable String id, @RequestHeader("Authorization") String auth) throws IOException {
        if (!PasswordUtil.ok(auth)) return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);

        Map<String, Object> result = new HashMap<>();
        NovaDevice dv = serverChannel.getDeviceByName(id);
        if (dv == null || !dv.enable())
            return new ResponseEntity<>(result.put("error", "device " + id + " not enabled"), HttpStatus.BAD_REQUEST);

        NovaTrafficServer ts = dv.obtainTrafficServer();
        result.put("allPlaylist", ts.getAllPlaylistId());
        result.put("nowPlayContent", ts.getNowPlayAllContent());
//        int i = ts.deletePlaylistById(1);
//        result.put("removePlaylist1", i);
//        i = ts.deletePlaylistById(2);
//        result.put("removePlaylist2", i);
//        i = ts.deletePlaylistById(5);
//        result.put("removePlaylist5", i);
        result.put("deviceType", ts.getDeviceType());
        result.put("allMediaFileName", ts.getAllMediaFileName());
        result.put("clientDevice", ts.getClientDevice());
        result.put("version", ts.getDeviceVersion());
        result.put("screenStatus", ts.getScreenStatus());
        result.put("getPlayByTimeList", ts.getPlayByTimeList());
        result.put("getRDSRoad", ts.getRDSRoad());


        ts.sendPlayList(1, "");

        return new ResponseEntity<>(result, HttpStatus.OK);
    }


    @PostMapping("/brightness")
    public ResponseEntity<?> setBrightness(@RequestBody int b) {
        if (b < 0 || b > 255) {
            return new ResponseEntity<>("invalid brightness, need to be from 0 to 255", HttpStatus.BAD_REQUEST);
        }
        for (NovaDevice dv : serverChannel.getAllDevices()) {
            if (dv == null || !dv.enable()) {
                System.out.println(dv + " not enabled");
                continue;
            }

            NovaTrafficServer ts = dv.obtainTrafficServer();

            int i = ts.setBrightness(b);
            if (i != 1) {
                return new ResponseEntity<>(String.format("set brightness failed, %d", i), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return new ResponseEntity<>(b, HttpStatus.OK);
    }

    @GetMapping("/brightness")
    public String getBrightness() {
        StringBuilder sb = new StringBuilder();
        for (NovaDevice dv : serverChannel.getAllDevices()) {
            if (dv == null || !dv.enable()) {
                System.out.println(dv + " not enabled");
                continue;
            }

            NovaTrafficServer ts = dv.obtainTrafficServer();

            sb.append(String.format("%s:%d", dv.getDeviceName(), ts.getDeviceType().getScreenBrightness()));
        }
        return sb.toString();
    }

    @GetMapping("/ss/{id}")
    public ResponseEntity<?> getScreenShot(@PathVariable String id) throws IOException {
        String response = "";
        NovaDevice dv = serverChannel.getDeviceByName(id);
        if (dv == null || !dv.enable()) {
            return new ResponseEntity<>("device " + id + " not enabled", HttpStatus.BAD_REQUEST);
        }
        NovaTrafficServer ts = dv.obtainTrafficServer();
        String fp = "/home/newuser/here.jpg";
        int ret = ts.getDeviceScreenshot(fp);
        if (ret != 1) {
            return new ResponseEntity<>("get screenshot failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        response = hash(Files.readAllBytes(Paths.get(fp)));

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/auth")
    public ResponseEntity<?> checkPW(@RequestHeader("Authorization") String auth) throws IOException {
        if (!PasswordUtil.ok(auth)) {
            return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);
        }

        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    public static class PasswordRequest {
        private String new_password;

        // standard getters and setters

        public String getNew_password() {
            return new_password;
        }
    }

    @PostMapping("/password")
    public ResponseEntity<?> setPassword(@RequestBody PasswordRequest passwordRequest, @RequestHeader("Authorization") String auth) throws IOException {
        String pw = passwordRequest.getNew_password();

        if (!PasswordUtil.ok(auth)) {
            return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);
        }

        if (!PasswordUtil.setPassword(pw)) {
            return new ResponseEntity<>("Invalid password, only support alphabet and numbers", HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrent() {
        String response = "";
        for (NovaDevice dv : serverChannel.getAllDevices()) {
            if (dv == null || !dv.enable()) {
                continue;
            }
            NovaTrafficServer ts = dv.obtainTrafficServer();
            DeviceNowPlayList c = ts.getNowPlayAllContent();
            response = String.format("%d\n%s", c.getPlayId(), c.getContent());
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private String formatDisplay(List<Display.Item> display) {
        String head_template = """
                [all]
                items=%d
                """;
        String item_param_template = """
                [item%d]
                param=%d,1,1,1,0,0,1
                """;
        String text_line_template = """
                txtext%d=10,%d,292,20,1,1616,1,%d,2,0,0,%s,00000000,0,1,%d,1,%s,0,0,0,0,0,0
                """;

        StringBuilder result = new StringBuilder();
        result.append(String.format(head_template, display.size()));
        for (int idx = 0; idx < display.size(); idx++) {
            Display.Item item = display.get(idx);
            int dur = item.getDuration() * 10;
            String color = item.getColor(); // white
            result.append(String.format(item_param_template, idx + 1, dur));
            List<List<String>> content = item.getContent();
            for (int i = 0; i < content.size(); i++) {
                List<String> row = content.get(i);
                for (int j = 0; j < row.size(); j++) {
                    String txt = row.get(j);
                    result.append(String.format(text_line_template,
                            i + 1,// which line, actually unnecessary
                            i * 20, // position
                            align[j], // horizontal alignment
                            color,
                            dur,
                            txt
                    ));
                }
            }
        }

        String res = result.toString();
        logger.info(res);
        return res;
    }

    private String formatPortrait(List<Display.Portrait> display) {
        int header_pos = 0;
        int footer_pos = 384;
        String ex_template = """
                txtext1=0,%d,0,64,arial,1414,1,2,2,3,0,%s,%s,0,1,100,1,%s,0,0,0,0,0,0
                """;
        String screen_prepare_ar = """
                txtext1=75,64,53,64,arial,1414,1,1,2,3,0,000000,FCE09B,0,1,100,1,الوقت: \\\\n الوجهة: \\\\n المسار: ,0,0,0,0,0,0
                txtext1=75,128,53,64,arial,1414,1,1,2,3,0,000000,B5CB99,0,1,100,1,الوقت: \\\\n الوجهة: \\\\n المسار: ,0,0,0,0,0,0
                txtext1=75,192,53,64,arial,1414,1,1,2,3,0,000000,FCE09B,0,1,100,1,الوقت: \\\\n الوجهة: \\\\n المسار: ,0,0,0,0,0,0
                txtext1=75,256,53,64,arial,1414,1,1,2,3,0,000000,B5CB99,0,1,100,1,الوقت: \\\\n الوجهة: \\\\n المسار: ,0,0,0,0,0,0
                txtext1=75,320,53,64,arial,1414,1,1,2,3,0,000000,FCE09B,0,1,101,1,الوقت: \\\\n الوجهة: \\\\n المسار: ,0,0,0,0,0,0
                """;
        String screen_template_ar = """
                txtext1=0,%d,75,64,arial,1414,1,2,2,3,0,%s,%s,0,1,%d,1,%s,0,0,0,0,0,0
                """;
        String screen_prepare_en = """
                txtext1=0,64,53,64,arial,1414,1,1,2,3,0,000000,B5CB99,0,1,100,1,Time: \\\\nDest: \\\\nRoute: ,0,0,0,0,0,0
                txtext1=0,128,53,64,arial,1414,1,1,2,3,0,000000,FCE09B,0,1,100,1,Time: \\\\nDest: \\\\nRoute: ,0,0,0,0,0,0
                txtext1=0,192,53,64,arial,1414,1,1,2,3,0,000000,B5CB99,0,1,100,1,Time: \\\\nDest: \\\\nRoute: ,0,0,0,0,0,0
                txtext1=0,256,53,64,arial,1414,1,1,2,3,0,000000,FCE09B,0,1,100,1,Time: \\\\nDest: \\\\nRoute: ,0,0,0,0,0,0
                txtext1=0,320,53,64,arial,1414,1,1,2,3,0,000000,B5CB99,0,1,101,2,Time: \\\\nDest: \\\\nRoute: ,0,0,0,0,0,0
                """;
        String screen_template_en = """               
                txtext1=53,%d,75,64,arial,1414,1,2,2,3,0,%s,%s,0,1,%d,1,%s,0,0,0,0,0,0
                """;
        String head_template = """
                [all]
                items=%d
                """;
        String item_param_template = """
                [item%d]
                param=%d,1,1,1,0,0,1
                """;
        String text_line_template = """
                txtext%d=10,%d,0,0,1,1616,1,%d,2,0,0,%s,00000000,0,1,%d,1,%s,0,0,0,0,0,0
                """;

        StringBuilder result = new StringBuilder();
        result.append(String.format(head_template, display.size()));
        for (int idx = 0; idx < display.size(); idx++) {
            Display.Portrait portrait = display.get(idx);
            int dur = portrait.getDuration() * 10;
            result.append(String.format(item_param_template, idx + 1, dur));

            Display.EX header = portrait.getHeader();
            if (null == header) {
                header = new Display.EX();
            }
            result.append(String.format(ex_template, header_pos, header.getColor(), header.getBackground(), header.getText()));

            Display.EX footer = portrait.getFooter();
            if (null == footer) {
                footer = new Display.EX();
            }
            result.append(String.format(ex_template, footer_pos, footer.getColor(), footer.getBackground(), footer.getText()));

            String color = portrait.getColor(); // white
            // primary and secondary color, can be configured.
            // when support configure, color need to support dynamic
            String[] bgColor = new String[]{"FCE09B", "B5CB99"};

            String prepare = screen_prepare_ar;
            String template = screen_template_ar;
            int bgColorIdx = 0;
            if (portrait.isIs_en()) {
                prepare = screen_prepare_en;
                template = screen_template_en;
                bgColorIdx = 1;
            }
            result.append(prepare);

            List<List<String>> content = portrait.getContent();
            while (content.size() < 5) {
                content.add(new ArrayList<>());
            }
            for (int i = 0; i < 5; i++) {
                List<String> strings = content.get(i);
                if (null == strings) {
                    strings = new ArrayList<>();
                }
                while (strings.size() < 3) {
                    strings.add("");
                }
                String txt = String.join("\\\\n", strings);
                result.append(String.format(template,
                        (i + 1) * 64, // y-axis position
                        color, // position
                        bgColor[(bgColorIdx + i) % 2], // horizontal alignment
                        dur,
                        txt
                ));
            }
        }

        String res = result.toString();
        logger.info(res);
        return res;
    }

    private String pushToScreen(String raw, String device) {
        NovaDevice dv = serverChannel.getDeviceByName(device);
        if (dv == null || !dv.enable()) {
            System.out.println(dv + " not enabled");
            return String.format("device %s is not enabled", device);
        }

        NovaTrafficServer ts = dv.obtainTrafficServer();

        int i = ts.sendPlayList(1, raw);
        logger.info("play status: " + i);

        return "";
    }

    public static String hash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data);
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            logger.error("The specified algorithm does not exist: " + e.getMessage());
        }
        return "not ok";
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
