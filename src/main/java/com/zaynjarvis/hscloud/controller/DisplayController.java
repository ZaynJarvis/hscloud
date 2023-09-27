package com.zaynjarvis.hscloud.controller;

import jakarta.annotation.PostConstruct;
import nova.traffic.been.DeviceNowPlayList;
import nova.traffic.been.PlayByTimeParam;
import nova.traffic.server.NovaDevice;
import nova.traffic.server.ServerChannel;
import nova.traffic.utils.LogConfig;
import nova.traffic.utils.NovaTrafficServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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
                System.out.println(ip + " connected");
            }
        });

        if (serverChannel.open("209.97.173.140", 9000)) {
            System.out.println("open channel success");
        } else {
            System.out.println("open channel failed");
        }
    }

    @PostMapping("/display")
    public ResponseEntity<?> createDisplay(@RequestBody List<Display.Item> display, @RequestHeader("Authorization") String auth) throws IOException {
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
        pushToScreen(d);

        return new ResponseEntity<>(d, HttpStatus.OK);
    }


    @PostMapping("/raw_display")
    public String createDisplay(@RequestBody String raw) {
        logger.info(raw);
        pushToScreen(raw);
        return raw;
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

    @GetMapping("/ss")
    public ResponseEntity<?> getScreenShot() throws IOException {
        String response = "";
        for (NovaDevice dv : serverChannel.getAllDevices()) {
            if (dv == null || !dv.enable()) {
                continue;
            }
            NovaTrafficServer ts = dv.obtainTrafficServer();
            String fp = "/home/newuser/here.jpg";
            int ret = ts.getDeviceScreenshot(fp);
            if (ret != 1) {
                return new ResponseEntity<>("get screenshot failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            response = hash(Files.readAllBytes(Paths.get(fp)));
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/auth")
    public ResponseEntity<?> checkPW(@RequestHeader("Authorization") String auth) throws IOException {
        if (!PasswordUtil.ok(auth)) {
            return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);
        }

        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    @PostMapping("/password")
    public ResponseEntity<?> setPassword(@RequestBody String pw, @RequestHeader("Authorization") String auth) throws IOException {
        if (!PasswordUtil.ok(auth)) {
            return new ResponseEntity<>("UNAUTH", HttpStatus.UNAUTHORIZED);
        }

        if (!PasswordUtil.setPassword(pw)) {
            return new ResponseEntity<>("invalid password, only support alphabet and numbers", HttpStatus.BAD_REQUEST);
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
                txtext%d=10,%d,0,0,1,1616,1,%d,2,0,0,%s,00000000,0,1,%d,1,%s,0,0,0,0,0,0
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
                            (i - 2) * 40, // position
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

    private void pushToScreen(String raw) {
        for (NovaDevice dv : serverChannel.getAllDevices()) {
            if (dv == null || !dv.enable()) {
                System.out.println(dv + " not enabled");
                continue;
            }

            NovaTrafficServer ts = dv.obtainTrafficServer();

            int i = ts.sendPlayList(1, raw);
            logger.info("play status: " + i);
        }
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
