package com.buaa01.illumineer_backend.service.impl.gain;

import com.buaa01.illumineer_backend.entity.CustomResponse;
import com.buaa01.illumineer_backend.entity.Paper;
import com.buaa01.illumineer_backend.entity.PaperAdo;
import com.buaa01.illumineer_backend.entity.User;
import com.buaa01.illumineer_backend.service.gain.GainAdoptService;
import com.buaa01.illumineer_backend.service.user.UserService;
import com.buaa01.illumineer_backend.tool.RedisTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.stereotype.Service;

import com.buaa01.illumineer_backend.service.client.PaperServiceClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GainAdoptServiceImpl implements GainAdoptService {
    @Autowired
    private PaperServiceClient paperServiceClient;

    @Autowired
    private RedisTool redisTool;
    @Autowired
    private UserService userService;
    @Autowired
    private JpaProperties jpaProperties;

    /**
     * 给出符合条件的文献
     * @param name 认领者的真实姓名
     * 需要将文章对象进行缓存，在完成认领的时候需要重新调用
     * **/
    @Override
    public List<PaperAdo> getAllGain(String name){
        String adoptionKey = "adoption :" + name;
        if(!redisTool.isExist(adoptionKey)){
        List<PaperAdo> paperAdoptions = paperServiceClient.getPaperAdoByName(name);
        //将带认领的文献列表存入redis中
        //在对应的document-service中将paper实体类放入redis
        for(PaperAdo paperAdo : paperAdoptions){
            redisTool.addSetMember(adoptionKey,paperAdo.getPid());
        }
        redisTool.setExpire(adoptionKey,300);
        return paperAdoptions;}
        else{
            List<Long> pids = redisTool.getAllList(adoptionKey,Long.class);
            return paperServiceClient.getPaperAdoByList(pids, name);
        }
    }

    /**
     * 根据认领结果对数据库中的Paper的Auth进行修改
     * @param pids 被认领的文章集合 uid 被认领对象的id
     */
    @Override
    public CustomResponse updateAdoption(List<Integer> pids, Integer uid){
        CustomResponse customResponse = new CustomResponse(200,"待修改",null);
        return customResponse;
    }

    /**
     * 给出符合条件的文献
     *
     * @param name 认领者的真实姓名
     *             需要将文章对象进行缓存，在完成认领的时候需要重新调用
     **/
    @Override
    public List<PaperAdo> getAllGainToClaim(String name) {
        String needClaimKey = "needClaim :" + name;
        if(!redisTool.isExist(needClaimKey)){
            //初始化key
            List<PaperAdo> paperAdoptions = paperServiceClient.getPaperAdoByName(name);
            for(PaperAdo paperAdoption : paperAdoptions){
                redisTool.addSetMember(needClaimKey,paperAdoption.getPid());
            }
            return paperAdoptions;
        }
        //已有
        //List<Long> pids = redisTool.getAllList(needClaimKey,Long.class);
        Set<Object> pids = redisTool.getSetMembers(needClaimKey);
        List<Long> longPids = pids.stream()
                .map(item -> {
                    try {
                        return Long.parseLong(item.toString());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid value: " + item);
                        return null; // 处理转换失败的情况
                    }
                })
                .filter(Objects::nonNull) // 过滤掉转换失败的 null 值
                .toList();
        System.out.println(longPids);
        return paperServiceClient.getPaperAdoByList(longPids,name);
    }

    /**
     * 给出符合条件的文献
     *
     * @param name 认领者的真实姓名
     *             需要将文章对象进行缓存，在完成认领的时候需要重新调用
     **/
    @Override
    public List<PaperAdo> getAllGainClaimed(String name) {
        String ClaimedKey = "Claimed :" + name;
        //键值的初始化在认领中完成
        if(!redisTool.isExist(ClaimedKey)){
            return List.of();
        }
        //List<Long> pids = redisTool.getAllList(ClaimedKey,Long.class);
        Set<Object> pids = redisTool.getSetMembers(ClaimedKey);
        List<Long> longPids = pids.stream()
                .map(item -> {
                    try {
                        return Long.parseLong(item.toString());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid value: " + item);
                        return null; // 处理转换失败的情况
                    }
                })
                .filter(Objects::nonNull) // 过滤掉转换失败的 null 值
                .toList();
        System.out.println(longPids);
        return paperServiceClient.getPaperAdoByList(longPids,name);
    }

    @Override
    public CustomResponse claimAPaper(Integer uid, Long pid) {
        User user = userService.getUserByUId(uid);
        String needClaimKey = "needClaim :" + user.getName();
        String ClaimedKey = "Claimed :" + user.getName();
        //处理显示集合
        redisTool.addSetMember(ClaimedKey,pid);
        redisTool.deleteSetMember(needClaimKey,pid);
        //处理表示归属的集合
        String paperList = "property:" + uid;
        String AuthList = "paperBelonged:" + pid;
        redisTool.addSetMember(paperList,pid);
        redisTool.addSetMember(AuthList,uid);

        //处理paper实体类的归属
        paperServiceClient.modifyAuth(pid, user.getName(), uid);
        return new CustomResponse(200,"成功认领",null);

    }


}
