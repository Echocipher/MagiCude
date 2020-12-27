package com.tiji.center.util;

import com.tiji.center.pojo.*;
import com.tiji.center.service.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.quartz.CronExpression;
import org.quartz.TriggerUtils;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.util.DigestUtils;
import util.IdWorker;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

/**
 * @author 贰拾壹
 * @create 2018-10-24 16:50
 */

public class TijiHelper {

    private static final String RE_DOMAIN = "[\\w-]+\\.(com\\.cn|net\\.cn|gov\\.cn|org\\.nz|org\\.cn|com|net|org|gov|cc|biz|info|cn|co|js)\\b()*";
    private static final Pattern DomainPattern = Pattern.compile(RE_DOMAIN, Pattern.CASE_INSENSITIVE);

    public static boolean agentOnline(AgentService agentService, IdWorker idWorker, RabbitMessagingTemplate rabbitMessagingTemplate) {
        List<Agent> onlineAgentList = agentService.findAllByOnline(true);
        // 没有agent在线，发agent心跳包监控
        // 在线则不管，状态由AgentHeartbeatMonitorScheduler刷新
        if (onlineAgentList.isEmpty()) {
            Map<String, String> taskConfig = new HashMap<>();
            taskConfig.put("status", "heartbeat");
            rabbitMessagingTemplate.convertAndSend("tijifanout", "", taskConfig);
            // 接收
            Message<?> agentConfigMessage = rabbitMessagingTemplate.receive("agentconfig");
            if (!Objects.isNull(agentConfigMessage)) {
                getAgentConfigMessage(agentService, idWorker, agentConfigMessage);
            }
        }
        return !onlineAgentList.isEmpty();
    }

    //TODO 处理agent cpu和内存状态
    public static void getAgentConfigMessage(AgentService agentService, IdWorker idWorker, Message<?> agentConfigMessage) {
        if (!Objects.isNull(agentConfigMessage)) {
            Map<String, String> agentConfig = (Map<String, String>) agentConfigMessage.getPayload();
            String agentName = agentConfig.get("agentName");
            String nmapPath = agentConfig.get("nmapPath");
            String massPath = agentConfig.get("massPath");
            String online = agentConfig.get("online");
            String ipAddress = agentConfig.get("ipAddress");
            String onlineFlag = online + ipAddress;

            if (!Objects.isNull(agentName) && !Objects.isNull(nmapPath) && !Objects.isNull(massPath) && !Objects.isNull(online)) {
                Agent dbAgent = agentService.findByNameAndIpaddress(agentName, ipAddress);
                //新增一个agent记录
                if (Objects.isNull(dbAgent)) {
                    agentService.add(new Agent(idWorker.nextId() + "", agentName, nmapPath, massPath, ipAddress, true, "0"));
                }
            }
            //
            if (!Objects.isNull(online)) {
                List<Agent> agentList = agentService.findAll();
                for (Agent agent : agentList) {
                    String name = agent.getName();
                    String ipaddress = agent.getIpaddress();
                    if (!onlineFlag.equals(name + ipaddress)) {
                        //agent.setOnline(false);
                    } else {
                        agent.setOnline(true);
                        agent.setTimeouts("0");
                    }
                    agentService.update(agent);
                }
            }
        }
    }

    //http parser 结果进数据库
    public static void httppResult2Db(WebinfoService webinfoService, UrlService urlService, IdWorker idWorker, TitlewhitelistService titlewhitelistService, DomainwhitelistService domainwhitelistService, WebrawdataService webrawdataService, Map<String, String> resultMap, String scanResult) {
        Date date = new Date();
        String webInfoId = idWorker.nextId() + "";
        String assetPortId = resultMap.get("assetPortId");
        String title = resultMap.get("title");
        String bodyWholeText = resultMap.get("bodyWholeText");
        String server = resultMap.get("server");
        String x_Powered_By = resultMap.get("x_Powered_By");
        String set_Cookie = resultMap.get("set_Cookie");
        String www_Authenticate = resultMap.get("www_Authenticate");

        String header = resultMap.get("header");
        String response = resultMap.get("response");


        List<Titlewhitelist> allTitlewhitelist = titlewhitelistService.findAll();
        List<String> allDomainwhitelist = domainwhitelistService.findAllDistinct();


        String titleWhiteListId = null;
        //title不在白名单里才记录bodyWholeText
        for (Titlewhitelist titlewhitelistItem : allTitlewhitelist) {
            String titlewhitelist = titlewhitelistItem.getTitle();
            if (title.contains(titlewhitelist)) {
                titleWhiteListId = titlewhitelistItem.getId();
                bodyWholeText = null;
            }
        }

        boolean addWebInfoFlag = true;
        //set_Cookie，sessionId会变，不加到md5中
        String md5Base = title + bodyWholeText + server + x_Powered_By + www_Authenticate;
        //计算MD5
        String incomeDigest = DigestUtils.md5DigestAsHex(md5Base.toLowerCase().getBytes());

        List<Webinfo> webinfoList = webinfoService.findByPortId(assetPortId);
        if (webinfoList.size() != 0) {
            for (Webinfo webinfo : webinfoList) {
                String title1 = webinfo.getTitle();
                String bodyWholeText1 = webinfo.getBodychildrenstextcontent();
                String server1 = webinfo.getServer();
                String x_Powered_By1 = webinfo.getXpoweredby();
                String www_Authenticate1 = webinfo.getWwwauthenticate();
                String dbBase = title1 + bodyWholeText1 + server1 + x_Powered_By1 + www_Authenticate1;
                String dbDigest = DigestUtils.md5DigestAsHex(dbBase.toLowerCase().getBytes());
                if (incomeDigest.equals(dbDigest)) {
                    addWebInfoFlag = false;
                }
            }
        }
        if (addWebInfoFlag) {
            //TODO 这里可以得到新增的web信息和url信息，
            // 如果是第一次跑，则全部的都是新增的
            // 如果是第二次+，则web信息有修改
            // 如果页面中包含时间戳..则每次都会新增
            //title为空也直接记录，防止title和body的text都为空时访问的记录丢失
            webinfoService.add(new Webinfo(webInfoId, assetPortId, titleWhiteListId, title, bodyWholeText,
                    server, x_Powered_By, set_Cookie, www_Authenticate, null, null, null, date));
            webrawdataService.add(new Webrawdata(idWorker.nextId() + "", webInfoId, header, response));
            if (!Objects.isNull(bodyWholeText)) {
                //此时scanResult只有urlNameAndLink
                if (!Objects.isNull(scanResult) && !scanResult.isEmpty()) {
                    //去掉最后的<+>
                    String relUrlNameAndLinks = scanResult.substring(0, scanResult.length() - 3);
                    String[] urlNameAndLinks = relUrlNameAndLinks.split("<\\+>");
                    for (String urlNameAndLink : urlNameAndLinks) {
                        String urlName = urlNameAndLink.split("<\\|>")[0];
                        String urlLink = urlNameAndLink.split("<\\|>")[1];
                        Matcher matcher = DomainPattern.matcher(urlLink);
                        //有域名
                        if (matcher.find()) {
                            //域名不在白名单
                            if (!allDomainwhitelist.contains(matcher.group(0))) {
                                urlService.add(new Url(idWorker.nextId() + "", webInfoId, urlName, urlLink));
                            }
                        } else {
                            urlService.add(new Url(idWorker.nextId() + "", webInfoId, urlName, urlLink));
                        }
                    }
                }
            }
        }
    }


    //nse扫描结果处理
    public static void nseResultParser(AssetipService assetipService, AssetportService assetportService, IdWorker idWorker, PluginconfigService pluginconfigService, CheckresultService checkresultService, VulnkeywordService vulnkeywordService, CheckresultVulnService checkresultVulnService, VulnpluginconfigService vulnpluginconfigService, ImvulnnotifyService imvulnnotifyService, RedisTemplate<String, String> redisTemplate, RabbitMessagingTemplate rabbitMessagingTemplate, String scanResult) {

        Date date = new Date();
        //scanResult第一行包含127.0.0.1 -p6379 --script redis-info
        String nseCommandTemp = scanResult.split("Starting")[0];
        String ip = nseCommandTemp.split("-p")[0].trim();
        String port = nseCommandTemp.split("-p")[1].split("\\s")[0].trim();
        String nseName = nseCommandTemp.split("--script")[1].trim();
        scanResult = scanResult.split("Starting")[1];

        //System.out.println(scanResult);
        //nse结果不包含|_，证明nse结果为空
        if (!scanResult.contains("|_")) {
            //结果为空，判断之前扫描情况
            //如果之前扫描到漏洞，则标记该漏洞修复
            //否则不做处理
            Assetip assetip = assetipService.findByIpaddressv4AndPassivetimeIsNull(ip);
            Assetport assetport = assetportService.findByAssetipidAndPortAndDowntimeIsNull(assetip.getId(), port);
            String assetPortId = assetport.getId();
            Checkresult checkresult = checkresultService.findByAssetportidAndNameAndPassivetimeIsNull(assetPortId, nseName);
            if (!Objects.isNull(checkresult)) {
                checkresult.setPassivetime(date);
                checkresultService.update(checkresult);
            }
        } else {
            //匹配nse结果
            String mulRegex = "(((?:^|\n)\\|.*){1,101}|((?:^|\n)\\|_.*))";
            Pattern pattern = Pattern.compile(mulRegex);
            Matcher matcher = pattern.matcher(scanResult);
            //单个nse结果，用if
            if (matcher.find()) {
                String nseResult = matcher.group(0);
                nseResult = nseResult.replaceAll("\\|\\s{2,}|\\|_\\s+", "@@@@@@@@@@@@@@");
                nseResult = nseResult.replaceAll("\\|\\s|\\|_", "!#-#!#-#!#-#!#-#!#-#!#-#!#-#!");
                nseResult = nseResult.split("!#-#!#-#!#-#!#-#!#-#!#-#!#-#!")[1];
                nseResult = nseResult.replaceAll("@", "");
                //去掉结果中的nseName:
                String nseResultReplaceNseName = nseResult.replaceAll(nseName + ":\\s*", "");
                pluginScanResult2Db(assetipService, assetportService, idWorker, pluginconfigService, checkresultService, vulnkeywordService, checkresultVulnService, vulnpluginconfigService, imvulnnotifyService, redisTemplate, rabbitMessagingTemplate, ip, port, nseName, nseResultReplaceNseName, "nse");
            }
        }
    }

    //selfd结果处理
    public static void selfdResultParser(AssetipService assetipService, AssetportService assetportService, IdWorker idWorker, PluginconfigService pluginconfigService, CheckresultService checkresultService, VulnkeywordService vulnkeywordService, CheckresultVulnService checkresultVulnService, VulnpluginconfigService vulnpluginconfigService, ImvulnnotifyService imvulnnotifyService, RedisTemplate<String, String> redisTemplate, RabbitMessagingTemplate rabbitMessagingTemplate, Map<String, String> resultMap, String scanResult) {
        String ip = resultMap.get("ip");
        String port = resultMap.get("port");
        String selfdName = resultMap.get("pluginName");
        pluginScanResult2Db(assetipService, assetportService, idWorker, pluginconfigService, checkresultService, vulnkeywordService, checkresultVulnService, vulnpluginconfigService, imvulnnotifyService, redisTemplate, rabbitMessagingTemplate, ip, port, selfdName, scanResult, "selfd");
    }

    //插件扫描结果进数据库
    private static void pluginScanResult2Db(AssetipService assetipService, AssetportService assetportService, IdWorker idWorker, PluginconfigService pluginconfigService, CheckresultService checkresultService, VulnkeywordService vulnkeywordService, CheckresultVulnService checkresultVulnService, VulnpluginconfigService vulnpluginconfigService, ImvulnnotifyService imvulnnotifyService, RedisTemplate<String, String> redisTemplate, RabbitMessagingTemplate rabbitMessagingTemplate, String ip, String port, String pluginName, String pluginScanResult, String pluginConfigType) {
        Date date = new Date();
        Pluginconfig pluginconfig = pluginconfigService.findByNameAndType(pluginName, pluginConfigType);
        String pluginConfigId = pluginconfig.getId();
        Map<String, String> searchMap = new HashMap<>();
        searchMap.put("pluginconfigid", pluginConfigId);
        List<Vulnkeyword> vulnkeywordList = vulnkeywordService.findSearch(searchMap);

        String validateType = pluginconfig.getValidatetype();
        //验证类型为http
        //HttpValidateKey_
        if (!Objects.isNull(validateType) && validateType.contains("http")) {
            //从redis中取key，设置成漏洞关键词
            String httpValidateKey = redisTemplate.opsForValue().get("HttpValidateKey_");
            vulnkeywordList.add(new Vulnkeyword(null, null, httpValidateKey));
        }

        Assetip assetip = assetipService.findByIpaddressv4AndPassivetimeIsNull(ip);
        Assetport assetport = assetportService.findByAssetipidAndPortAndDowntimeIsNull(assetip.getId(), port);
        String assetPortId = assetport.getId();
        Checkresult checkresult = checkresultService.findByAssetportidAndNameAndPassivetimeIsNull(assetPortId, pluginName);
        //漏洞不存在或者修复后又有了
        if (Objects.isNull(checkresult)) {
            for (Vulnkeyword vulnkeyword : vulnkeywordList) {
                String keyword = vulnkeyword.getKeyword();
                //如果nseResultReplaceNseName包含漏洞关键字，存在漏洞，新增checkresult
                if (pluginScanResult.contains(keyword)) {
                    //新增 checkresult
                    String checkResultId = idWorker.nextId() + "";
                    String risk = pluginconfig.getRisk();
                    checkresultService.add(new Checkresult(checkResultId, assetPortId, pluginName, pluginScanResult, risk, date, null, null));
                    //更新检测结果漏洞中间表
                    //获取当前pluginConfigId对应的所有插件配置
                    List<Vulnpluginconfig> vulnpluginconfigList = vulnpluginconfigService.findAllByPluginConfigId(pluginConfigId);
                    //如果一个插件对应多个漏洞
                    List<String> vulnIdList = new ArrayList<>();
                    vulnpluginconfigList.forEach(vulnpluginconfig -> {
                        String vulnId = vulnpluginconfig.getVulnid();
                        vulnIdList.add(vulnId);
                        checkresultVulnService.add(new CheckresultVuln(idWorker.nextId() + "", checkResultId, vulnId));
                    });

                    sendCheckResutl2ImResultQueue(imvulnnotifyService, redisTemplate, rabbitMessagingTemplate, ip, port, pluginName, assetip, assetport, risk, vulnIdList);
                    //vulnResultList:[Redis未授权访问] 192.168.12.138 6379 redis Redis key-value store RedisInfo 高危
                    //漏洞名称	风险	ip	端口	服务	版本	检测插件名称
                    //System.out.println("vulnResultList:" + vulnResultList + " " + risk + " " + ip + " " + port + " " + service + " " + version + " " + pluginName);
                    //TODO 立即发送邮件

                    break;
                }
            }
        } else {
            //漏洞存在
            boolean existFlag = true;
            for (Vulnkeyword vulnkeyword : vulnkeywordList) {
                String keyword = vulnkeyword.getKeyword();
                //如果nseResultReplaceNseName不包含漏洞关键字，不存在漏洞，漏洞已修复
                if (pluginScanResult.contains(keyword)) {
                    existFlag = false;
                    break;
                }
            }
            if (existFlag) {
                //TODO 已修复漏洞
                checkresult.setPassivetime(date);
                checkresultService.update(checkresult);
            }
        }
    }

    private static void sendCheckResutl2ImResultQueue(ImvulnnotifyService imvulnnotifyService, RedisTemplate<String, String> redisTemplate, RabbitMessagingTemplate rabbitMessagingTemplate, String ip, String port, String pluginName, Assetip assetip, Assetport assetport, String risk, List<String> vulnIdList) {
        boolean sendWFlag = false;
        boolean sendDFlag = false;

        String wechatnotifyKey = "wechatnotify";
        String dingtalknotifyKey = "dingtalknotify";
        String riskKey = "risk";

        Boolean wechatnotifyFlagExist = redisTemplate.hasKey(wechatnotifyKey);
        if (!Objects.isNull(wechatnotifyFlagExist) && wechatnotifyFlagExist) {
            String wechatnotify = redisTemplate.opsForValue().get(wechatnotifyKey);
            if (!Objects.isNull(wechatnotify) && "true".equals(wechatnotify)) {
                sendWFlag = true;
            }
        }

        Boolean dingtalknotifyFlagExist = redisTemplate.hasKey(dingtalknotifyKey);
        if (!Objects.isNull(dingtalknotifyFlagExist) && dingtalknotifyFlagExist) {
            String dingtalknotify = redisTemplate.opsForValue().get(wechatnotifyKey);
            if (!Objects.isNull(dingtalknotify) && "true".equals(dingtalknotify)) {
                sendDFlag = true;
            }
        } else {
            Imvulnnotify imvulnnotify = imvulnnotifyService.findAll().get(0);
            //钉钉
            Boolean dingtalknotify = imvulnnotify.getDingtalknotify();
            if (!Objects.isNull(dingtalknotify) && dingtalknotify) {
                sendDFlag = true;
                redisTemplate.opsForValue().set(dingtalknotifyKey, "true");
            }
            //企微
            Boolean wechatnotify = imvulnnotify.getWechatnotify();
            if (!Objects.isNull(wechatnotify) && wechatnotify) {
                sendWFlag = true;
                redisTemplate.opsForValue().set(wechatnotifyKey, "true");
            }
        }
        if (sendDFlag || sendWFlag) {
            Boolean riskKeyFlagExist = redisTemplate.hasKey(riskKey);
            String notifyRisk;
            if (!Objects.isNull(riskKeyFlagExist) && riskKeyFlagExist) {
                notifyRisk = redisTemplate.opsForValue().get(riskKey);
            } else {
                Imvulnnotify imvulnnotify = imvulnnotifyService.findAll().get(0);
                notifyRisk = imvulnnotify.getRisk();
                redisTemplate.opsForValue().set(riskKey, notifyRisk);
            }
            //判断risk
            if (!StringUtils.isEmpty(notifyRisk) && notifyRisk.contains(risk)) {
                String service = assetport.getService();
                String version = assetport.getVersion();
                Map<String, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("projectinfoid", assetip.getProjectinfoid());
                resultMap.put("vulnIdList", vulnIdList);
                resultMap.put("risk", risk);
                resultMap.put("ip", ip);
                resultMap.put("port", port);
                resultMap.put("service", service);
                resultMap.put("version", version);
                resultMap.put("pluginName", pluginName);
                rabbitMessagingTemplate.convertAndSend("imresult", resultMap);
            }
        }
    }


    //一个端口，且端口相同的IP,分到一组
    public static void iPWithSamePorts2OneGroup(Map<String, Set<String>> dstMap, Map<String, Set<String>> srcMap, BlockingQueue<String> massIpQueue) throws InterruptedException {
        for (Map.Entry<String, Set<String>> entry : srcMap.entrySet()) {
            //StringUtils将Set中的端口转换成str
            String ip = entry.getKey();
            Set<String> portSet = entry.getValue();
            //一个端口，且端口相同,分到一组
            if (portSet.size() == 1) {
                String port = portSet.iterator().next();
                removeMultiKeyWithSameValue(dstMap, ip, port);
            } else {//端口数大于2
                String ports = StringUtils.join(portSet, ",");
                massIpQueue.put(ip + " -p" + ports);
            }
        }
    }

    public static Map<String, Set<String>> ipAndPortList2Map(List<String> ipAndPortList) {
        Map<String, Set<String>> resultMap = new LinkedHashMap<>();
        //多次扫描，ip可能会重复，此时如果直接put到map中，会造成数据丢失，需要比较
        //将resultList中的结果去重，存入massResultMap
        for (String line : ipAndPortList) {
            String ip = line.split(",")[0];
            String port = line.split(",")[1];
            removeMultiKeyWithSameValue(resultMap, port, ip);
        }
        return resultMap;
    }


    //mass扫描结果进数据库
    public static void massScanResult2DB(Map<String, Set<String>> resultMap, TaskipService taskipService, TaskportService taskportService, IdWorker idWorker, String taskId) {
        if (resultMap.size() != 0) {
            List<Taskip> taskipList = new LinkedList<>();
            for (Map.Entry<String, Set<String>> entry : resultMap.entrySet()) {
                String ip = entry.getKey();
                Set<String> portInfoSet = entry.getValue();
                List<Taskport> taskPortList = new LinkedList<>();
                Taskip taskip = taskipService.findByTaskidAndIpaddressv4(taskId, ip);
                String taskIpId;
                //ip在数据库中不存在，直接新增
                if (Objects.isNull(taskip)) {
                    //TODO 本次任务新增的ip和端口
                    taskIpId = idWorker.nextId() + "";
                    taskip = new Taskip(taskIpId, taskId, ip, null, false);
                    //taskipList.add(taskip);
                    taskipService.add(taskip);
                    portInfoSet.forEach(portInfoString -> {
                        //taskPortList.add(new Taskport(idWorker.nextId() + "", taskIpId, portInfoString, null, "open", null, null));
                        taskportService.add(new Taskport(idWorker.nextId() + "", taskIpId, portInfoString, "tcp", "open", null, null, false));
                    });
                } else {
                    //当前ip在数据库中存在,判断端口
                    taskIpId = taskip.getId();
                    portInfoSet.forEach(portInfoString -> {
                        Taskport taskport = taskportService.findByTaskipidAndPort(taskIpId, portInfoString);
                        //端口不在数据库中，直接新增
                        if (Objects.isNull(taskport)) {
                            //taskPortList.add(new Taskport(idWorker.nextId() + "", taskIpId, portInfoString, null, "open", null, null));
                            taskportService.add(new Taskport(idWorker.nextId() + "", taskIpId, portInfoString, "tcp", "open", null, null, false));
                        }
                        //否则不做处理
                    });
                    //taskportService.batchAdd(taskPortList);
                }
            }
            //taskipService.batchAdd(taskipList);
        }
    }

    //nmap扫描结果进数据库
    public static void nmapScanResult2DB(Map<Map<String, String>, Set<String>> resultMap, TaskipService taskipService, TaskportService taskportService, IdWorker idWorker, String taskId) {
        if (!Objects.isNull(resultMap)&&!resultMap.isEmpty()) {
            List<Taskip> taskipList = new LinkedList<>();
//            for (Map.Entry<String, Set<String>> entry : resultMap.entrySet()) {
            resultMap.forEach((map, portInfoSet) -> {
                List<Taskport> taskPortList = new LinkedList<>();
                String ip = map.get("ip");
                Taskip taskip = taskipService.findByTaskidAndIpaddressv4(taskId, ip);
                String taskIpId;
                //ip在数据库中不存在，直接新增
                if (Objects.isNull(taskip)) {
                    //TODO 本次任务新增的ip和端口
                    taskIpId = idWorker.nextId() + "";
                    taskip = new Taskip(taskIpId, taskId, ip, null, false);
                    //taskipList.add(taskip);
                    taskipService.add(taskip);
                } else {
                    //当前ip在数据库中存在,判断端口
                    taskIpId = taskip.getId();
                    //taskportService.batchAdd(taskPortList);
                }
                //非ping扫描，有端口信息
                if (!Objects.isNull(portInfoSet)) {
                    portInfoSet2Db(taskportService, idWorker, portInfoSet, taskIpId);
                }
            });
            //taskipService.batchAdd(taskipList);
        }
    }

    private static void portInfoSet2Db(TaskportService taskportService, IdWorker idWorker, Set<String> portInfoSet, String taskIpId) {
        portInfoSet.forEach(portInfoString -> {
            String[] portInfoStringArrays = portInfoString.split(",");
            String portTemp = portInfoStringArrays[0];
            String protocolTemp = portInfoStringArrays[1];
            String stateTemp = portInfoStringArrays[2];
            String serviceTemp = portInfoStringArrays[3];
            String versionTemp = portInfoStringArrays[4];
            Taskport dbTaskport = taskportService.findByTaskipidAndPort(taskIpId, portTemp);
            //taskPortList.add(new Taskport(idWorker.nextId() + "", taskIpId, portInfoStringArrays[0], portInfoStringArrays[1], portInfoStringArrays[2], portInfoStringArrays[3], portInfoStringArrays[4]));
            if (Objects.isNull(dbTaskport)) {
                taskportService.add(new Taskport(idWorker.nextId() + "", taskIpId, portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, false));
            } else {
                boolean flag = false;
                //端口在数据库中
                //更新protocol
                if (!Objects.isNull(protocolTemp) && (Objects.isNull(dbTaskport.getProtocol()) || "null".equals(dbTaskport.getProtocol()))) {
                    dbTaskport.setProtocol(protocolTemp);
                    flag = true;
                }
                //更新tcp/udp
                if (!Objects.isNull(protocolTemp) && !Objects.isNull(dbTaskport.getProtocol()) && !"null".equals(dbTaskport.getProtocol())) {
                    if (!dbTaskport.getProtocol().contains("/")) {
                        if (("tcp".equals(protocolTemp) && "udp".equals(dbTaskport.getProtocol())) || "udp".equals(protocolTemp) && "tcp".equals(dbTaskport.getProtocol())) {
                            dbTaskport.setProtocol("tcp/udp");
                            flag = true;
                        }
                    }
                }
                //更新state
                if ("open".equals(stateTemp) && (Objects.isNull(dbTaskport.getState()) || !"open".equals(dbTaskport.getState()))) {
                    dbTaskport.setState(stateTemp);
                    flag = true;
                }
                //更新service
                if (
                        (!Objects.isNull(serviceTemp) && !"tcpwrapped".equals(serviceTemp) && !"unknown".equals(serviceTemp) && !serviceTemp.contains("?"))
                                && (Objects.isNull(dbTaskport.getService()) || "tcpwrapped".equals(dbTaskport.getService()) || "unknown".equals(dbTaskport.getService()) || dbTaskport.getService().contains("?") || "null".equals(dbTaskport.getService()) || !serviceTemp.equals(dbTaskport.getService()))
                ) {
                    dbTaskport.setService(serviceTemp);
                    flag = true;
                }
                //更新version
                if ((!Objects.isNull(versionTemp) && !"null".equals(versionTemp))
                        && (Objects.isNull(dbTaskport.getVersion()) || "null".equals(dbTaskport.getVersion()) || !versionTemp.equals(dbTaskport.getVersion()))) {
                    dbTaskport.setVersion(versionTemp);
                    flag = true;
                }
                if (flag) {
                    taskportService.update(dbTaskport);
                }
            }
        });
    }

    //mass扫描结果直接进资产
    public static void massScanResult2AssetDB(AssetipService assetipService, AssetportService assetportService, IdWorker idWorker, RabbitMessagingTemplate rabbitMessagingTemplate, Set<String> riskPortSet, Set<String> riskServiceSet, Set<String> riskVersionSet, Map<String, Set<String>> massResultMap) {
        if (massResultMap.size() != 0) {
            Date date = new Date();
            List<Assetip> assetipList = new LinkedList<>();
            massResultMap.forEach((ip, portInfoSet) -> {
                //当前ip端口列表，用于批量增加
                List<Assetport> portList = new LinkedList<>();
                String assetIpId;
                //查询数据库中passivetime为空且ipaddressv4等于当前ip的ip
                Assetip assetip = assetipService.findByIpaddressv4AndPassivetimeIsNull(ip);
                //ip在数据库中不存在，直接新增
                if (Objects.isNull(assetip)) {
                    //如果结果返回空
                    //数据库中没有当前ip或者所有的ip都已下线，新增ip及端口
                    //TODO mass扫描新增ip和端口
                    assetIpId = idWorker.nextId() + "";
                    assetip = new Assetip(assetIpId, null, ip, null, false, false, date, null, null);
                    //assetipList.add(assetip);
                    assetipService.add(assetip);
                    portInfoSet.forEach(portInfoString -> {
                        //portList.add(new Assetport(idWorker.nextId() + "", assetIpId, portInfoString, null, "open", null, null, date, null));
                        assetportService.add(new Assetport(idWorker.nextId() + "", assetIpId, portInfoString, "tcp", "open", null, null, false, false, date, null, null));
                        //新增高危信息端口发送到IM
                        sendRiskPort2IM(rabbitMessagingTemplate, riskPortSet, riskServiceSet, riskVersionSet, "**" + ip + "**", portInfoString, "tcp", null, null);
                    });
                } else {
                    //当前ip在数据库中存在,更新端口信息
                    for (String portInfoString : portInfoSet) {
                        //mass扫描结果
                        Assetport dbPort = assetportService.findByAssetipidAndPortAndDowntimeIsNull(assetip.getId(), portInfoString);
                        if (Objects.isNull(dbPort)) {
                            //当前端口不在DB中或者当前端口已下线，新增端口
                            //TODO 已存在数据库中ip的新增端口
                            //portList.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portInfoString, null, "open", null, null, date, null));
                            assetportService.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portInfoString, "tcp", "open", null, null, false, false, date, null, null));
                            //新增高危信息端口发送到IM
                            sendRiskPort2IM(rabbitMessagingTemplate, riskPortSet, riskServiceSet, riskVersionSet, ip, portInfoString, "tcp", null, null);
                        } else {
                            //当前端口在DB中，更新端口状态
                            boolean flag = false;
                            if (dbPort.getState() == null || !"open".equals(dbPort.getState())) {
                                dbPort.setState("open");
                                dbPort.setUptime(date);
                                dbPort.setChangedtime(date);
                                flag = true;
                            }
                            if (flag) {
                                assetportService.update(dbPort);
                            }
                        }
                    }
                }
                //assetportService.batchAdd(portList);
            });
            //assetipService.batchAdd(assetipList);
        }
    }


    //nmap扫描结果直接进资产
    public static void nmapScanResult2AssetDB(AssetipService assetipService, AssetportService assetportService, HostService hostService, IdWorker idWorker, RabbitMessagingTemplate rabbitMessagingTemplate, Set<String> riskPortSet, Set<String> riskServiceSet, Set<String> riskVersionSet, Map<Map<String, String>, Set<String>> nmapResultMap) {
        if (!Objects.isNull(nmapResultMap)&&!nmapResultMap.isEmpty()) {
            Date date = new Date();
            List<Assetip> assetipList = new LinkedList<>();
//            for (Map.Entry<String, Set<String>> entry : nmapResultMap.entrySet()) {
            nmapResultMap.forEach((map, portInfoSet) -> {
                //当前ip端口列表，用于批量增加
                String ip = map.get("ip");
                List<Assetport> portList = new LinkedList<>();
                String assetIpId;

                //查询数据库中passivetime为空且ipaddressv4等于当前ip的ip
                Assetip assetip = assetipService.findByIpaddressv4AndPassivetimeIsNull(ip);
                if (Objects.isNull(assetip)) {
                    //如果结果返回空
                    //数据库中没有当前ip或者所有的ip都已下线，新增ip及端口
                    //TODO nmap扫描新增ip和端口
                    assetIpId = idWorker.nextId() + "";
                    assetip = new Assetip(assetIpId, null, ip, null, false, false, date, null, null);
                    //assetipList.add(assetip);
                    assetipService.add(assetip);
                    if (!Objects.isNull(portInfoSet)) {
                        for (String portInfoString : portInfoSet) {
                            String[] portInfoStringArrays = portInfoString.split(",");
                            String portTemp = portInfoStringArrays[0];
                            String protocolTemp = portInfoStringArrays[1];
                            String stateTemp = portInfoStringArrays[2];
                            String serviceTemp = portInfoStringArrays[3];
                            String versionTemp = portInfoStringArrays[4];
                            //状态为open的端口才进资产库
                            if ("open".equals(stateTemp)) {
                                Assetport dbAssetPort = assetportService.findByAssetipidAndPortAndDowntimeIsNull(assetip.getId(), portTemp);
                                //批量导入时，如果单个ip端口重复，数据库中端口会重复
                                //需要判断端口是否在数据库中才新增
                                if (Objects.isNull(dbAssetPort)) {
                                    assetportService.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, false, false, date, null, null));
                                    //新增高危信息端口发送到IM
                                    sendRiskPort2IM(rabbitMessagingTemplate, riskPortSet, riskServiceSet, riskVersionSet, "**" + ip + "**", portTemp, protocolTemp, serviceTemp, versionTemp);
                                    //portList.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, date, null));
                                } else {
                                    //当前端口在DB中，更新端口
                                    boolean flag = false;
                                    //更新protocol
                                    if (!Objects.isNull(protocolTemp) && (Objects.isNull(dbAssetPort.getProtocol()) || "null".equals(dbAssetPort.getProtocol()))) {
                                        dbAssetPort.setProtocol(protocolTemp);
                                        flag = true;
                                    }
                                    //更新tcp/udp
                                    if (!Objects.isNull(protocolTemp) && !Objects.isNull(dbAssetPort.getProtocol()) && !"null".equals(dbAssetPort.getProtocol())) {
                                        if (!dbAssetPort.getProtocol().contains("/")) {
                                            if (("tcp".equals(protocolTemp) && "udp".equals(dbAssetPort.getProtocol())) || "udp".equals(protocolTemp) && "tcp".equals(dbAssetPort.getProtocol())) {
                                                dbAssetPort.setProtocol("tcp/udp");
                                                flag = true;
                                            }
                                        }
                                    }
                                    //更新state
                                    if ((Objects.isNull(dbAssetPort.getState()) || !"open".equals(dbAssetPort.getState()))) {
                                        dbAssetPort.setState(stateTemp);
                                        flag = true;
                                    }
                                    //更新service
                                    if (
                                            (!Objects.isNull(serviceTemp) && !"tcpwrapped".equals(serviceTemp) && !"unknown".equals(serviceTemp) && !serviceTemp.contains("?"))
                                                    && (Objects.isNull(dbAssetPort.getService()) || "tcpwrapped".equals(dbAssetPort.getService()) || "unknown".equals(dbAssetPort.getService()) || dbAssetPort.getService().contains("?") || "null".equals(dbAssetPort.getService()) || !serviceTemp.equals(dbAssetPort.getService()))
                                    ) {
                                        dbAssetPort.setService(serviceTemp);
                                        flag = true;
                                    }
                                    //version为空或者null，才更新version
                                    //加新verison
                                    if ((!Objects.isNull(versionTemp) && !"null".equals(versionTemp))
                                            && (Objects.isNull(dbAssetPort.getVersion()) || "null".equals(dbAssetPort.getVersion()) || !versionTemp.equals(dbAssetPort.getVersion()))) {
                                        dbAssetPort.setVersion(versionTemp);
                                        flag = true;
                                    }
                                    if (flag) {
                                        dbAssetPort.setChangedtime(date);
                                        assetportService.update(dbAssetPort);
                                    }
                                }
                            }
                        }
                    }


                } else {
                    //当前ip在数据库中存在,更新端口信息
                    assetIpId = assetip.getId();
                    if (!Objects.isNull(portInfoSet)) {
                        for (String portInfoString : portInfoSet) {

                            //nmap扫描结果
                            //port,protocol,state,service,version
                            //3306,tcp,open,mysql,MySQL 5.7.24-log
                            String[] portInfoStringArrays = portInfoString.split(",");
                            String portTemp = portInfoStringArrays[0];
                            String protocolTemp = portInfoStringArrays[1];
                            String stateTemp = portInfoStringArrays[2];
                            String serviceTemp = portInfoStringArrays[3];
                            String versionTemp = portInfoStringArrays[4];

                            Assetport dbAssetPort = assetportService.findByAssetipidAndPortAndDowntimeIsNull(assetIpId, portTemp);
                            if (Objects.isNull(dbAssetPort)) {
                                //当前端口不在DB中或者当前端口已下线，且端口状态是open，新增端口
                                //TODO 已在数据库中ip的新增端口
                                if ("open".equals(stateTemp)) {
                                    //portList.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, date, null));
                                    assetportService.add(new Assetport(idWorker.nextId() + "", assetIpId, portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, false, false, date, null, null));
                                    //已在数据库中的ip 新增高危信息端口发送到IM
                                    sendRiskPort2IM(rabbitMessagingTemplate, riskPortSet, riskServiceSet, riskVersionSet, ip, portTemp, protocolTemp, serviceTemp, versionTemp);
                                }
                            } else {
                                //当前端口在DB中，更新端口
                                boolean flag = false;
                                boolean sendFlag = false;
                                //如果扫描到端口已关闭，将端口下线
                                if ("closed".equals(stateTemp)) {
                                    dbAssetPort.setState("closed");
                                    dbAssetPort.setDowntime(date);
                                    flag = true;
                                } else {
                                    //更新protocol
                                    if (!Objects.isNull(protocolTemp) && (Objects.isNull(dbAssetPort.getProtocol()) || "null".equals(dbAssetPort.getProtocol()))) {
                                        dbAssetPort.setProtocol(protocolTemp);
                                        flag = true;
                                    }
                                    //更新tcp/udp
                                    if (!Objects.isNull(protocolTemp) && !Objects.isNull(dbAssetPort.getProtocol()) && !"null".equals(dbAssetPort.getProtocol())) {
                                        if (!dbAssetPort.getProtocol().contains("/")) {
                                            if (("tcp".equals(protocolTemp) && "udp".equals(dbAssetPort.getProtocol())) || "udp".equals(protocolTemp) && "tcp".equals(dbAssetPort.getProtocol())) {
                                                dbAssetPort.setProtocol("tcp/udp");
                                                flag = true;
                                            }
                                        }
                                    }
                                    //更新state
                                    if ("open".equals(stateTemp) && (Objects.isNull(dbAssetPort.getState()) || !"open".equals(dbAssetPort.getState()))) {
                                        dbAssetPort.setState(stateTemp);
                                        flag = true;
                                        sendFlag = true;
                                    }
                                    //更新service
                                    if (
                                            (!Objects.isNull(serviceTemp) && !"tcpwrapped".equals(serviceTemp) && !"unknown".equals(serviceTemp) && !serviceTemp.contains("?"))
                                                    && (Objects.isNull(dbAssetPort.getService()) || "tcpwrapped".equals(dbAssetPort.getService()) || "unknown".equals(dbAssetPort.getService()) || dbAssetPort.getService().contains("?") || "null".equals(dbAssetPort.getService()) || !serviceTemp.equals(dbAssetPort.getService()))
                                    ) {
                                        dbAssetPort.setService(serviceTemp);
                                        flag = true;
                                        sendFlag = true;
                                    }
                                    //更新version
                                    if ((!Objects.isNull(versionTemp) && !"null".equals(versionTemp))
                                            && (Objects.isNull(dbAssetPort.getVersion()) || "null".equals(dbAssetPort.getVersion())) || !versionTemp.equals(dbAssetPort.getVersion())) {
                                        dbAssetPort.setVersion(versionTemp);
                                        flag = true;
                                        sendFlag = true;
                                    }
                                }
                                if (flag) {
                                    dbAssetPort.setChangedtime(date);
                                    assetportService.update(dbAssetPort);
                                }
                                //已在数据库中的ip，已存在的端口，新增高危信息端口发送到IM
                                if (sendFlag) {
                                    sendRiskPort2IM(rabbitMessagingTemplate, riskPortSet, riskServiceSet, riskVersionSet, ip, portTemp, protocolTemp, serviceTemp, versionTemp);
                                }
                            }

                        }
                    }
                }
                //assetportService.batchAdd(portList);
                //hostname / domain
                //域名
                String domain = map.get("domain");
                if (!Objects.isNull(domain)) {
                    Host hostname = hostService.findByHostname(domain);
                    if (Objects.isNull(hostname)) {
                        hostService.add(new Host(idWorker.nextId() + "", assetIpId, map.get("macAddress"), domain, null, null, map.get("type"), null, new Date(), null));
                    }
                }
                String macAddress = map.get("macAddress");
                if (!Objects.isNull(macAddress)) {
                    Host macaddress = hostService.findByMacaddress(macAddress);
                    if (Objects.isNull(macaddress)) {
                        hostService.add(new Host(idWorker.nextId() + "", assetIpId, map.get("macAddress"), map.get("domain"), null, null, map.get("type"), null, new Date(), null));
                    }
                }
            });
            //assetipService.batchAdd(assetipList);
        }
    }

    private static void sendRiskPort2IM(RabbitMessagingTemplate rabbitMessagingTemplate, Set<String> riskPortSet, Set<String> riskServiceSet, Set<String> riskVersionSet, String ip, String portTemp, String protocolTemp, String serviceTemp, String versionTemp) {
        if (!riskPortSet.isEmpty() || !riskServiceSet.isEmpty() || !riskVersionSet.isEmpty()) {
            boolean sendFlag = false;
            Map<String, Object> imResultMap = new HashMap<>();
            //端口在高危设置中，服务和版本也要一起推送
            if (riskPortSet.contains(portTemp) || riskServiceSet.contains(serviceTemp) || riskVersionSet.contains(versionTemp)) {
                if (!StringUtils.isEmpty(portTemp)) {
                    imResultMap.put("port", portTemp);
                    sendFlag = true;
                }
                if (!StringUtils.isEmpty(serviceTemp)) {
                    imResultMap.put("service", serviceTemp);
                    sendFlag = true;
                }else {
                    imResultMap.put("service", "null");
                }
                if (!StringUtils.isEmpty(versionTemp)) {
                    imResultMap.put("version", versionTemp);
                    sendFlag = true;
                }else {
                    imResultMap.put("version", "null");
                }
            }
            if (sendFlag) {
                //新增ip的高危端口信息
                imResultMap.put("ip", ip);
                imResultMap.put("protocol", protocolTemp);
                rabbitMessagingTemplate.convertAndSend("imresult", imResultMap);
            }
        }
    }

    //批量导入
    public static void batchNmapScanResult2AssetDB(AssetipService assetipService, AssetportService assetportService, HostService hostService, IdWorker idWorker, Map<String, Set<String>> nmapResultMap) {
        if (nmapResultMap.size() != 0) {
            Date date = new Date();
            List<Assetip> assetipList = new LinkedList<>();
//            for (Map.Entry<String, Set<String>> entry : nmapResultMap.entrySet()) {
            nmapResultMap.forEach((ip, portInfoSet) -> {
                //当前ip端口列表，用于批量增加
                List<Assetport> portList = new LinkedList<>();
                String assetIpId;

                //查询数据库中passivetime为空且ipaddressv4等于当前ip的ip
                Assetip assetip = assetipService.findByIpaddressv4AndPassivetimeIsNull(ip);
                if (Objects.isNull(assetip)) {
                    //如果结果返回空
                    //数据库中没有当前ip或者所有的ip都已下线，新增ip及端口
                    //TODO nmap扫描新增ip和端口
                    assetIpId = idWorker.nextId() + "";
                    assetip = new Assetip(assetIpId, null, ip, null, false, false, date, null, null);
                    //assetipList.add(assetip);
                    assetipService.add(assetip);
                    if (!Objects.isNull(portInfoSet)) {
                        for (String portInfoString : portInfoSet) {
                            String[] portInfoStringArrays = portInfoString.split(",");
                            String portTemp = portInfoStringArrays[0];
                            String protocolTemp = portInfoStringArrays[1];
                            String stateTemp = portInfoStringArrays[2];
                            String serviceTemp = portInfoStringArrays[3];
                            String versionTemp = portInfoStringArrays[4];
                            //状态为open的端口才进资产库
                            if ("open".equals(stateTemp)) {
                                Assetport dbAssetPort = assetportService.findByAssetipidAndPortAndDowntimeIsNull(assetip.getId(), portTemp);
                                //批量导入时，如果单个ip端口重复，数据库中端口会重复
                                //需要判断端口是否在数据库中才新增
                                if (Objects.isNull(dbAssetPort)) {
                                    assetportService.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, false, false, date, null, null));
                                    //portList.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, date, null));
                                } else {
                                    //当前端口在DB中，更新端口
                                    boolean flag = false;
                                    //更新protocol
                                    if (!Objects.isNull(protocolTemp) && (Objects.isNull(dbAssetPort.getProtocol()) || "null".equals(dbAssetPort.getProtocol()))) {
                                        dbAssetPort.setProtocol(protocolTemp);
                                        flag = true;
                                    }
                                    //更新tcp/udp
                                    if (!Objects.isNull(protocolTemp) && !Objects.isNull(dbAssetPort.getProtocol()) && !"null".equals(dbAssetPort.getProtocol())) {
                                        if (!dbAssetPort.getProtocol().contains("/")) {
                                            if (("tcp".equals(protocolTemp) && "udp".equals(dbAssetPort.getProtocol())) || "udp".equals(protocolTemp) && "tcp".equals(dbAssetPort.getProtocol())) {
                                                dbAssetPort.setProtocol("tcp/udp");
                                                flag = true;
                                            }
                                        }
                                    }
                                    //更新state
                                    if ((Objects.isNull(dbAssetPort.getState()) || !"open".equals(dbAssetPort.getState()))) {
                                        dbAssetPort.setState(stateTemp);
                                        flag = true;
                                    }
                                    //更新service
                                    if (
                                            (!Objects.isNull(serviceTemp) && !"tcpwrapped".equals(serviceTemp) && !"unknown".equals(serviceTemp) && !serviceTemp.contains("?"))
                                                    && (Objects.isNull(dbAssetPort.getService()) || "tcpwrapped".equals(dbAssetPort.getService()) || "unknown".equals(dbAssetPort.getService()) || dbAssetPort.getService().contains("?") || "null".equals(dbAssetPort.getService()) || !serviceTemp.equals(dbAssetPort.getService()))
                                    ) {
                                        dbAssetPort.setService(serviceTemp);
                                        flag = true;
                                    }
                                    //version为空或者null，才更新version
                                    //加新verison
                                    if ((!Objects.isNull(versionTemp) && !"null".equals(versionTemp))
                                            && (Objects.isNull(dbAssetPort.getVersion()) || "null".equals(dbAssetPort.getVersion()) || !versionTemp.equals(dbAssetPort.getVersion()))) {
                                        dbAssetPort.setVersion(versionTemp);
                                        flag = true;
                                    }
                                    if (flag) {
                                        dbAssetPort.setChangedtime(date);
                                        assetportService.update(dbAssetPort);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    //当前ip在数据库中存在,更新端口信息
                    assetIpId = assetip.getId();
                    if (!Objects.isNull(portInfoSet)) {
                        for (String portInfoString : portInfoSet) {

                            //nmap扫描结果
                            //port,protocol,state,service,version
                            //3306,tcp,open,mysql,MySQL 5.7.24-log
                            String[] portInfoStringArrays = portInfoString.split(",");
                            String portTemp = portInfoStringArrays[0];
                            String protocolTemp = portInfoStringArrays[1];
                            String stateTemp = portInfoStringArrays[2];
                            String serviceTemp = portInfoStringArrays[3];
                            String versionTemp = portInfoStringArrays[4];

                            Assetport dbAssetPort = assetportService.findByAssetipidAndPortAndDowntimeIsNull(assetIpId, portTemp);
                            if (Objects.isNull(dbAssetPort)) {
                                //当前端口不在DB中或者当前端口已下线，且端口状态是open，新增端口
                                //TODO 已在数据库中ip的新增端口
                                if ("open".equals(stateTemp)) {
                                    //portList.add(new Assetport(idWorker.nextId() + "", assetip.getId(), portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, date, null));
                                    assetportService.add(new Assetport(idWorker.nextId() + "", assetIpId, portTemp, protocolTemp, stateTemp, serviceTemp, versionTemp, false, false, date, null, null));
                                }
                            } else {
                                //当前端口在DB中，更新端口
                                boolean flag = false;
                                //如果扫描到端口已关闭，将端口下线
                                if ("closed".equals(stateTemp)) {
                                    dbAssetPort.setState("closed");
                                    dbAssetPort.setDowntime(date);
                                    flag = true;
                                } else {
                                    //更新protocol
                                    if (!Objects.isNull(protocolTemp) && (Objects.isNull(dbAssetPort.getProtocol()) || "null".equals(dbAssetPort.getProtocol()))) {
                                        dbAssetPort.setProtocol(protocolTemp);
                                        flag = true;
                                    }
                                    //更新tcp/udp
                                    if (!Objects.isNull(protocolTemp) && !Objects.isNull(dbAssetPort.getProtocol()) && !"null".equals(dbAssetPort.getProtocol())) {
                                        if (!dbAssetPort.getProtocol().contains("/")) {
                                            if (("tcp".equals(protocolTemp) && "udp".equals(dbAssetPort.getProtocol())) || "udp".equals(protocolTemp) && "tcp".equals(dbAssetPort.getProtocol())) {
                                                dbAssetPort.setProtocol("tcp/udp");
                                                flag = true;
                                            }
                                        }
                                    }
                                    //更新state
                                    if ("open".equals(stateTemp) && (Objects.isNull(dbAssetPort.getState()) || !"open".equals(dbAssetPort.getState()))) {
                                        dbAssetPort.setState(stateTemp);
                                        flag = true;
                                    }
                                    //更新service
                                    if (
                                            (!Objects.isNull(serviceTemp) && !"tcpwrapped".equals(serviceTemp) && !"unknown".equals(serviceTemp) && !serviceTemp.contains("?"))
                                                    && (Objects.isNull(dbAssetPort.getService()) || "tcpwrapped".equals(dbAssetPort.getService()) || "unknown".equals(dbAssetPort.getService()) || dbAssetPort.getService().contains("?") || "null".equals(dbAssetPort.getService()) || !serviceTemp.equals(dbAssetPort.getService()))
                                    ) {
                                        dbAssetPort.setService(serviceTemp);
                                        flag = true;
                                    }
                                    //更新version
                                    if ((!Objects.isNull(versionTemp) && !"null".equals(versionTemp))
                                            && (Objects.isNull(dbAssetPort.getVersion()) || "null".equals(dbAssetPort.getVersion())) || !versionTemp.equals(dbAssetPort.getVersion())) {
                                        dbAssetPort.setVersion(versionTemp);
                                        flag = true;
                                    }
                                }
                                if (flag) {
                                    dbAssetPort.setChangedtime(date);
                                    assetportService.update(dbAssetPort);
                                }
                            }
                        }
                    }
                }
                //assetportService.batchAdd(portList);
            });
            //assetipService.batchAdd(assetipList);
        }
    }

    //合并Map中相同value（单个）的key
    //如a:1,b:1,c:1=》1：a,b,c
    private static void removeMultiKeyWithSameValue(Map<String, Set<String>> massRawMap, String value, String key) {
        Set<String> set;
        if (massRawMap.containsKey(key)) {
            set = massRawMap.get(key);
        } else {
            set = new LinkedHashSet<>();
        }
        set.add(value);
        massRawMap.put(key, set);
    }

    public static Map<Map<String, String>, Set<String>> nmapResult2Map(String result) {
        //portInfo port, state, service, version;
        Map<Map<String, String>, Set<String>> resultMap = new LinkedHashMap<>();
        String regex = "All\\s.*|(?:^|are\\sclosed\n)MAC\\sAddress\\s|(?:^|\n)Nmap\\sscan\\sreport\\s|(?:^|\n)Starting\\sNmap.*|(?:^|\n)Host.*|(?:^|\n)Not shown.*|(?:^|\n)Some\\sclosed .*|(?:^|\n)PORT.*|(?:^|\n)[0-9]?\\sservice.?\\sunrecognized.*|(?:^|\n)SF.*|(?:^|\n)Starting.*|(?:^|\n)Warning.*|(?:^|\n)={14}.*|(?:^|\n)Service\\sdetection.*|(?:^|\n)Nmap done.*";
        result = result.replaceAll(regex, "");
        if (result.length() != 1) {
            if ((result.contains("tcp") || result.contains("udp"))) {
                //端口扫描
                //正则采用NFA，递归过深会导致Exception in thread "main" java.lang.StackOverflowError
                //{1,500}限制单个ip匹配端口数，测试中超过770个就会导致栈溢出
                //匹配IP及对应服务
                //String mulRegex = "for\\s(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\n?(((?:^|\n)[0-9].*){1,500})";
                String ipRegex = "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})";
                String[] tempResults = result.split("\\n");
                String singleLine = tempResults[0];
                singleLine = singleLine.replaceAll(ipRegex, "");
                singleLine = singleLine.replaceAll("[()]", "");
                String[] split = singleLine.split("\\s");
                //没有域名
                if (split.length == 1) {
                    String mulRegex = "for\\s(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\n?(((?:^|\n)[0-9].*){1,500})";
                    Pattern pattern = Pattern.compile(mulRegex);
                    Matcher matcher = pattern.matcher(result);
                    while (matcher.find()) {
                        //singleStatus[0]:PORT，singleStatus[1]:STATE，singleStatus[2]:SERVICE，singleStatus[3]:VERSION
                        String ip = matcher.group(1);
                        String status = matcher.group(2);
                        //单行IP状态，PORT     STATE    SERVICE       VERSION
                        //用于保存分割后的状态
                        //将lineStatus分解成单个数组元素，并保存到singleStatus中
                        String[] lineStatus = status.split("\n");
                        if (!org.springframework.util.StringUtils.isEmpty(ip)) {
                            Map<String, String> map = new HashMap<>();
                            map.put("ip", ip);
                            macAddress2Map(result, resultMap, tempResults, lineStatus, map);
                        }
                    }
                } else {
                    String replaceRegex = "Other\\saddresses\\sfor.*|(?:^|\n)rDNS\\srecord\\sfor.*|(?:^|\n)Nmap\\sscan\\sreport\\s|(?:^|\n)Starting\\sNmap.*|(?:^|\n)Host.*|(?:^|\n)Not shown.*|(?:^|\n)Some\\sclosed .*|(?:^|\n)PORT.*|(?:^|\n)[0-9]?\\sservice.?\\sunrecognized.*|(?:^|\n)SF.*|(?:^|\n)Starting.*|(?:^|\n)Warning.*|(?:^|\n)={14}.*|(?:^|\n)Service\\sdetection.*|(?:^|\n)Nmap done.*";
                    result = result.replaceAll(replaceRegex, "");
                    String mulRegex = "for\\s(.*)\n?(((?:^|\n)[0-9].*){1,500})";
                    Pattern pattern = Pattern.compile(mulRegex);
                    Matcher matcher = pattern.matcher(result);
                    while (matcher.find()) {
                        //singleStatus[0]:PORT，singleStatus[1]:STATE，singleStatus[2]:SERVICE，singleStatus[3]:VERSION
                        String domainAndIp = matcher.group(1);
                        String domain = domainAndIp.replaceAll("[()]", "").split("\\s")[0];
                        String ip = domainAndIp.replaceAll("[()]", "").split("\\s")[1];
                        String status = matcher.group(2);
                        //单行IP状态，PORT     STATE    SERVICE       VERSION
                        //用于保存分割后的状态
                        //将lineStatus分解成单个数组元素，并保存到singleStatus中
                        String[] lineStatus = status.split("\n");
                        if (!org.springframework.util.StringUtils.isEmpty(ip)) {
                            Map<String, String> map = new HashMap<>();
                            map.put("ip", ip);
                            map.put("domain", domain);
                            macAddress2Map(result, resultMap, tempResults, lineStatus, map);
                        }
                    }
                }


            } else if (!result.contains("tcp") && !result.contains("udp")) { //nmap -sn，ping扫描
                result = result.replaceAll("\\)for\\s", "<+>");
                result = result.replaceAll("for\\s", "");
                result = result.replaceAll("\n", "");
                Set<String> resultSet = new HashSet<>(0);
                String[] ipAndMacAddr = result.split("<\\+>");
                for (String line : ipAndMacAddr) {
                    if (!org.springframework.util.StringUtils.isEmpty(line)) {
                        Map<String, String> map = new HashMap<>();
                        String ip = line;
                        if (line.contains("MAC Address: ")) {
                            ip = line.split("MAC Address: ")[0];
                            String temp = line.split("MAC Address: ")[1];
                            if (temp.contains("(")) {
                                String macAddr = temp.split("\\s\\(")[0];
                                String type = temp.split("\\s\\(")[1];
                                map.put("macAddress", macAddr);
                                map.put("type", type);
                            }
                        }
                        if (!org.springframework.util.StringUtils.isEmpty(ip)) {
                            map.put("ip", ip);
                            resultMap.put(map, resultSet);
                        }
                    }
                }
            }
        }
        return resultMap.isEmpty() ? null : resultMap;
    }

    private static void macAddress2Map(String result, Map<Map<String, String>, Set<String>> resultMap, String[] tempResults, String[] lineStatus, Map<String, String> map) {
        if (result.contains("MAC Address")) {
            String macAddrTemp = tempResults[tempResults.length - 1];
            String s = macAddrTemp.split(":\\s")[1];
            String macAddr = s;
            if (s.contains("(")) {
                String type = s.split("\\s\\(")[1];
                type = type.replace(")", "");
                map.put("type", type);
                macAddr = s.split("\\s\\(")[0];
            }
            map.put("macAddress", macAddr);
        }
        resultMap.put(map, lineStatus2Set(lineStatus));
    }


    private static Set<String> lineStatus2Set(String[] targetLine) {
        Set<String> resultSet = new HashSet<>();
        for (String line : targetLine) {
            String[] singleStatus = new String[5];
            //去掉第一个空元素
            if (line.length() == 0) {
                continue;
            } else {
                //按空格分割成数组
                String[] statusArray = line.split("\\s+");
                //对PORT、protocol、STATE、SERVICE赋值
                singleStatus[0] = statusArray[0].split("/")[0];//port
                singleStatus[1] = statusArray[0].split("/")[1];//protocol
                singleStatus[2] = statusArray[1];//STATE
                singleStatus[3] = statusArray[2];//VERVICE

                //对VERSION赋值，如果statusArray等于3，则VERSION为null
                if (statusArray.length > 3) {
                    String[] version = Arrays.copyOfRange(statusArray, 3, statusArray.length);
                    StringBuilder stringBuilder = new StringBuilder();
                    //空格分割VERSION中的单词，并去掉末尾的空格
                    for (int i = 0; i < version.length; i++) {
                        if (i != version.length - 1) {
                            stringBuilder.append(version[i]).append(" ");
                        } else {
                            stringBuilder.append(version[i]);
                        }
                    }
                    singleStatus[4] = stringBuilder.toString();
                } else {
                    singleStatus[4] = null;
                }
            }
            String singleStatusString = Arrays.asList(singleStatus).toString();
            //去掉逗号后面的空格
            singleStatusString = singleStatusString.replaceAll(",\\s", ",");
            resultSet.add(singleStatusString.replace("[", "").replace("]", ""));
        }
        return resultSet;
    }

    public static Map<String, Set<String>> ipAndPortList2Map(String result) {

        //Discovered open port 21/tcp on 192.168.1.1
        String mulRegex = "([1-9][0-9]*)/\\w+\\son\\s(\\d+\\.\\d+\\.\\d+\\.\\d+)";
        Pattern pattern = Pattern.compile(mulRegex);
        Matcher matcher = pattern.matcher(result);
        Map<String, Set<String>> resultMap = new LinkedHashMap<>();
        List<String> resultList = new ArrayList<>();
        while (matcher.find()) {
            String ports = matcher.group(1);
            String ip = matcher.group(2);
            resultList.add(ip + "-" + ports);
        }
        //多次扫描，ip可能会重复，此时如果直接put到map中，会造成数据丢失，需要比较
        //将resultList中的结果去重，存入massResultMap
        resultList.forEach(line -> {
            String ip = line.split("-")[0];
            String port = line.split("-")[1];
            removeMultiKeyWithSameValue(resultMap, port, ip);
        });

        return resultMap;
    }

    public static void targetIPSet2SliceIPSet(String worktype, String targetport, String dstkey, String srckey, RedisTemplate<String, String> redisTemplate, String ipslicesize, String portslicesize) {

        int sliceSize = 255;
        if (!Objects.isNull(ipslicesize) && !"".equals(ipslicesize)) {
            sliceSize = Integer.parseInt(ipslicesize);
        }
        long ipGroupNumber = redisTemplate.opsForSet().size(srckey) / sliceSize;

        BlockingQueue<String> rawIpQueue = new LinkedBlockingQueue<>(redisTemplate.opsForSet().members(srckey));
        for (long i = 0; i <= ipGroupNumber; i++) {
            List<String> list = new ArrayList<>();
            rawIpQueue.drainTo(list, sliceSize);
            //nmap模式下全端口分组，mass模式不管它
            if ("nmap".equals(worktype) && (Objects.isNull(targetport) || targetport.isEmpty()) && !Objects.isNull(portslicesize) && !portslicesize.isEmpty()) {
                //默认1000端口为一组
                int pSliceSize = 1000;
                //分组范围1000-10000，其他则按默认1000大小进行分组
                if (Integer.parseInt(portslicesize) >= 1000 && Integer.parseInt(portslicesize) <= 10000) {
                    pSliceSize = Integer.parseInt(portslicesize);
                }
                int portGroupNumber = 65535 / pSliceSize;
                StringBuilder stringBuilder = new StringBuilder();
                int start, end;
                for (int j = 0, k = 1; j <= portGroupNumber; j++) {
                    start = k;
                    stringBuilder.append(start).append("-");
                    k += pSliceSize;
                    end = k - 1;
                    if (end >= 65535) {
                        end = 65535;
                    }
                    stringBuilder.append(end).append(",");
                }

                String[] portsArr = stringBuilder.toString().split(",");
                for (String ports : portsArr) {
                    String targetIp = list.toString().replaceAll("[,\\[\\]]", "");
                    if (!targetIp.isEmpty()) {
                        redisTemplate.opsForList().rightPush(dstkey, list.toString().replaceAll("[,\\[\\]]", "") + " -p" + ports);
                    }
                }

            } else {
                //分组可能为空
                if (list.size() != 0) {
                    redisTemplate.opsForList().rightPush(dstkey, list.toString());
                }
            }
        }
    }


    public static void target2Redis(String targetIp, RedisTemplate<String, String> redisTemplate, String key) {
        if (Objects.isNull(targetIp))
            return;
        //单个ip
        if (!targetIp.contains(",") && !targetIp.contains("-") && !targetIp.contains("/")) {
            redisTemplate.opsForSet().add(key, targetIp);
        }
        String[] targetIps = targetIp.split(",");
        for (String singleIp : targetIps) {
            //ip段
            if (singleIp.contains("-")) {
                Long start = ip2num(singleIp.split("-")[0]);
                Long end = ip2num(singleIp.split("-")[1]);
                LongStream.rangeClosed(start, end).forEach(ip -> {
                    if ((ip & 0xff) == 0)
                        return;
                    redisTemplate.opsForSet().add(key, num2ip(ip));
                });
            } else if (singleIp.contains("/")) {
                //CIDR表示的地址，将其转换成IP
                SubnetUtils subnetUtils = new SubnetUtils(singleIp);
                String[] IPs = subnetUtils.getInfo().getAllAddresses();
                for (String ip : IPs) {
                    redisTemplate.opsForSet().add(key, ip);
                }
            } else {
                //单个ip
                redisTemplate.opsForSet().add(key, singleIp);
            }
        }

    }

    private static Long ip2num(String ip) {
        String[] ips = ip.split("\\.");
        return Long.parseLong(ips[0]) << 24 | Long.parseLong(ips[1]) << 16 | Long.parseLong(ips[2]) << 8 | Long.parseLong(ips[3]);
    }

    private static String num2ip(Long num) {
        return ((num >> 24) & 0xff) + "." + ((num >> 16) & 0xff) + "." + ((num >> 8) & 0xff) + "." + (num & 0xff);
    }

    public static Map<String, Object> cronParseResult(String cronExpression) throws ParseException {
        Map<String, Object> runResultMap = new LinkedHashMap<>();
        Map<String, String> resultMap = new LinkedHashMap<>();
        if (org.springframework.util.StringUtils.isEmpty(cronExpression) || !CronExpression.isValidExpression(cronExpression)) {
            runResultMap.put("解析失败", "Cron表达式错误");
            return runResultMap;
        }
        CronTriggerImpl cronTriggerImpl = new CronTriggerImpl();
        cronTriggerImpl.setCronExpression(cronExpression);
        List<Date> dates = TriggerUtils.computeFireTimes(cronTriggerImpl, null, 10);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date dateNow = new Date();
        String now = sdf.format(dateNow);
        resultMap.put("当前时间", now);
        for (int i = 1; i <= dates.size(); i++) {
            if (!Objects.isNull(dates.get(i - 1))) {
                resultMap.put("第" + i + "次执行", sdf.format(dates.get(i - 1)));
            }
        }
        runResultMap.put("解析结果", resultMap);
        return runResultMap;
    }

}
