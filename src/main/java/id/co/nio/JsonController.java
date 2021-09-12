package id.co.nio;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.github.kpavlov.jreactive8583.client.Iso8583Client;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static id.co.nio.PackagerConfig.isoLengthMap;
import static id.co.nio.PackagerConfig.isoTypeMap;

@RestController
@RequestMapping(path = "/",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = {RequestMethod.GET, RequestMethod.POST})
public class JsonController implements  IsoMessageListener<IsoMessage> {

//    @Autowired
    Iso8583Client<IsoMessage> iso8583Client;

    private final Map<String,CountDownLatch> latchMap = new HashMap<>();
    private final Map<String,IsoMessage> futureResult = new HashMap<>();

    public JsonController(Iso8583Client<IsoMessage> iso8583Client) {
        this.iso8583Client = iso8583Client;
    }

    @PostMapping("/isorequest")
    public Map<String, String> topup(@RequestBody String request){

        iso8583Client.addMessageListener(this);

        Map<String, String> result = new LinkedHashMap<>();

        JSONParser parser = new JSONParser();

        JSONObject jsonRequest;
        try {
            jsonRequest = (JSONObject) parser.parse(request);
        } catch (ParseException e) {
            e.printStackTrace();
            result.put("error","request contains malformed JSON");
            return result;
        }

        //System.out.println(request);
        try {

            IsoMessage msgRequest = iso8583Client.getIsoMessageFactory().newMessage(0x200);

            for (Integer x = 2; x<128;x++) {
                String val = (String) jsonRequest.get(String.valueOf(x));
                if (Strings.isNotEmpty(val)) {
                    IsoType isoType = isoTypeMap.get(x);
                    Integer isoLength = isoLengthMap.get(x);
                    msgRequest.setValue(x,val, isoType, isoLength==null?0:isoLength);
                }
            }

            String identifier = msgRequest.getField(7).toString()+msgRequest.getField(11).toString();

            CountDownLatch latch = new CountDownLatch(1);

            logger.warn("Putting {} into latch map",identifier);
            latchMap.put(identifier,latch);

            iso8583Client.send(msgRequest);

            latch.await();

            logger.warn("getting " + identifier);
            IsoMessage response = futureResult.get(identifier);

            futureResult.remove(identifier);
            latchMap.remove(identifier);

            if (response == null) {
                logger.error("bodoh");
                result.put("7",jsonRequest.get("7").toString());
                result.put("11",jsonRequest.get("11").toString());
                result.put("39","06");
                return result;
            }

            for (int i = 0; i <= 128; i++) {
                if (i == 1) continue;
                //logger.warn("setting {}", i);
                IsoValue<String> value = response.getField(i);
                if (value != null) {
                    if (!StringUtils.isEmpty(value.getValue())) {
                        result.put(i + "", value.toString());
                    }
                }
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }


    @Override
    public boolean applies(@NotNull IsoMessage isoMessage) {
        return isoMessage.getType() == 0x210;
    }

    @Override
    public boolean onMessage(@NotNull ChannelHandlerContext ctx, @NotNull IsoMessage isoMessage) {
        logger.info("onMessage");
        String identifier = isoMessage.getField(7).toString()+isoMessage.getField(11).toString();
        logger.info("lookup  {}",identifier);
        CountDownLatch latch = latchMap.get(identifier);
        logger.info("------------ latch {}",latch.toString());
        logger.warn("welcoming {}",identifier);
        futureResult.put(identifier,isoMessage);
        latch.countDown();
        logger.warn("latch ........ {}",latch.getCount());
        //ctx.flush();
        return false
                ;
    }

    static final Logger logger = LoggerFactory.getLogger(JsonController.class);
}
