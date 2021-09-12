package id.co.nio;

import com.solab.iso8583.IsoType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xml.sax.InputSource;

import javax.annotation.PostConstruct;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.File;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Component
public class PackagerConfig {

    public static Map<Integer, IsoType> isoTypeMap = new HashMap<>();
    public static Map<Integer, Integer> isoLengthMap = new HashMap<>();

    @PostConstruct
    public void parsePackager() throws Exception {

        String fullFilePath = this.getClass().getClassLoader().getResource(".").getPath() + "j8583.xml";

        System.out.println(fullFilePath);

        String fileContent = readFile(new File(fullFilePath));

        //System.out.println(fileContent);

        J8583Config isopackager = unmarshal(fileContent);

        for (Parse p : isopackager.getParse()) {
            for (Field f : p.field) {
//                System.out.println(f.num+":"+f.type+" "+f.length+" - "+f.getvalue());

                Integer fieldNo = Integer.valueOf(f.num);
                IsoType existingIsoType = isoTypeMap.get(fieldNo);
                IsoType readIsoType = IsoType.valueOf(f.type);

                if (existingIsoType==null) {
                    //System.out.println("Populating "+f.num);
                    isoTypeMap.put(Integer.valueOf(f.num),readIsoType);
                    isoLengthMap.put(Integer.valueOf(f.num), StringUtils.isEmpty(f.length)?null:Integer.valueOf(f.length));
                } else {
                    if (existingIsoType.equals(readIsoType)) {
                        Integer existingLength = isoLengthMap.get(Integer.valueOf(f.num));
                        if (existingLength==null && StringUtils.isEmpty(f.length)) {
                            //its okay
                        } else {
                            if (existingLength != Integer.parseInt(f.length)) {
                                System.err.println("Packager LENGTH is not consistent on field "+f.num);
                            }
                        }
                    } else {
                        System.err.println("Packager ISOTYPE is not consistent on field "+f.num);
                    }
                }
            }
        }

        System.out.println(isoTypeMap.size());
        System.out.println(isoLengthMap.size());
    }

    private String readFile(File file) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        LineIterator it = FileUtils.lineIterator(file, "UTF-8");
        try {
            while (it.hasNext()) {
                String line = it.nextLine();
                stringBuilder.append(line);
                stringBuilder.append("\n");
            }
        } finally {
            it.close();
        }

        return stringBuilder.toString();
    }

    private J8583Config unmarshal(String fileName) throws Exception {
        //Disable XXE
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        spf.setValidating(false);
        spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        //Do unmarshall operation
        Source xmlSource = new SAXSource(spf.newSAXParser().getXMLReader(),
                new InputSource(new StringReader(fileName)));

        JAXBContext jc = JAXBContext.newInstance(J8583Config.class);

        Unmarshaller um = jc.createUnmarshaller();
        um.setSchema(null);
        return (J8583Config) um.unmarshal(xmlSource);
    }
}
