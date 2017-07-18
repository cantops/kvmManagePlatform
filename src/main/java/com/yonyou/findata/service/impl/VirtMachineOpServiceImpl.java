package com.yonyou.findata.service.impl;

import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import com.yonyou.findata.dao.MachineInfoMapper;
import com.yonyou.findata.model.MachineInfo;
import com.yonyou.findata.model.MachineInfoExample;
import com.yonyou.findata.protocol.KvmProtocol;
import com.yonyou.findata.protocol.MachineProtocol;
import com.yonyou.findata.service.KvmConfigService;
import com.yonyou.findata.service.VirtMachineOpService;
import com.yonyou.findata.ssh.SSHUtil;
import com.yonyou.findata.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * @author: pizhihui
 * @datae: 2017-07-14
 */
@Service
@Transactional
public class VirtMachineOpServiceImpl implements VirtMachineOpService {

    private static final Logger logger = LoggerFactory.getLogger(VirtMachineOpServiceImpl.class);

    @Autowired
    private KvmConfigService kvmConfigService;
    @Autowired
    private MachineInfoMapper machineInfoMapper;

    @Override
    public void installVirtMachine(MachineInfo info) {
        // 获取配置项
        List<String> configs = kvmConfigService.getAllConfigs(info.getIp());
        createRemoteFile(info.getHostIp(), info.getName(), configs);
        // 执行安装命令
        String installCmd = KvmProtocol.getInstallRunShell(info.getMem(), info.getCpu(), info.getName());
        logger.info("start install with cmd : {}", installCmd);
        //SSHUtil.execute(installCmd, info.getHostIp());
        // 存储数据库
        info.setState("running"); // 默认状态
        info.setType(1);
        machineInfoMapper.insert(info);
    }




    // 创建配置文件并拷贝到需要安装虚拟机的物理机的磁盘上
    private void createRemoteFile(String host, String name, List<String> content) {
        // 临时存放配置文件目录
        String tempPathFile = MachineProtocol.TEMP_DIR + String.format(KvmProtocol.CONFIG_FILE, name);
        Path path = Paths.get(MachineProtocol.TEMP_DIR, String.format(KvmProtocol.CONFIG_FILE, name));
        try {
            BufferedWriter writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"));
            try {
                for (String line : content) {
                    writer.write(line);
                    writer.newLine();
                }
            } finally {
                if (null != writer) {
                    writer.close();
                }
            }
        } catch (IOException e) {
            logger.error("write kvm install config file error, host: {}, name: {}, content: {} e", host, name, content, e);
        }

        // 将配置文件拷贝到远程机器上
        logger.info("cp local file to remote machine");
        SSHUtil.scpLocalToRemote(host, tempPathFile, MachineProtocol.CONF_DIR);
    }


}