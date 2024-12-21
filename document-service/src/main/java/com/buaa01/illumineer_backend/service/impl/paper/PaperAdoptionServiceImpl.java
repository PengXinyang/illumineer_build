package com.buaa01.illumineer_backend.service.impl.paper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.buaa01.illumineer_backend.entity.*;
import com.buaa01.illumineer_backend.mapper.PaperMapper;
import com.buaa01.illumineer_backend.service.paper.PaperAdoptionService;
import com.buaa01.illumineer_backend.tool.RedisTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class PaperAdoptionServiceImpl implements PaperAdoptionService {

    @Autowired
    private PaperMapper paperMapper;

    @Autowired
    private RedisTool redisTool;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    /***
     * 返回该用户已认领的文献
     * @param name 姓名
     * **/
    @Override
    public CustomResponse getPaperBelongedByName(String name){
        CustomResponse customResponse = new CustomResponse();
        int total = 0;

        List<PaperAdo> paperAdos = getPapersBelonged(name, true);
        total = paperAdos.size();

        Map<String, Object> result = new HashMap<>();
        result.put("result", paperAdos);
        result.put("total", total);
        customResponse.setData(result);
        return customResponse;
    }

    /***
     * 根据作者姓名返回包含该姓名的认领条目列表
     * @param name 姓名
     * **/
    @Override
    public CustomResponse getPaperAdoptionsByName(String name){
        CustomResponse customResponse = new CustomResponse();
        int total = 0;

        List<PaperAdo> paperAdos = getPapersBelonged(name, false);
        total = paperAdos.size();

        Map<String, Object> result = new HashMap<>();
        result.put("result", paperAdos);
        result.put("total", total);
        customResponse.setData(result);
        return customResponse;
    }

    /***
     * 根据pids中的各个pid找到Paper，转换成PaperAdo并返回
     * @param pids
     * **/
    @Override
    public List<PaperAdo> getPaperAdoptionsByList(List<Long> pids) {
        List<PaperAdo> paperAdos = new ArrayList<>();
        for (Long pid : pids) {
            Paper paper = null;
            QueryWrapper<Paper> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("pid", pid);
            paper = paperMapper.selectOne(queryWrapper);

            PaperAdo paperAdo = new PaperAdo(pid, paper.getTitle(), paper.getAuths(), paper.getPublishDate(), paper.getStats(), false);
            paperAdos.add(paperAdo);
        }
        return paperAdos;
    }

    /***
     * 根据category返回该category的认领条目列表
     * @param category
     * @param total 总数
     * **/
    @Override
    public List<PaperAdo> getPaperAdoptionsByCategory(Category category, Integer total) {
        List<PaperAdo> paperAdos = new ArrayList<>();
        List<Paper> papers = null;
        List<Long> pids = new ArrayList<>();
        QueryWrapper<Paper> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category_id", category.getMainClassId());
        papers = paperMapper.selectList(queryWrapper);
        for (Paper paper: papers) {
            pids.add(paper.getPid());
        }
        paperAdos = getPaperAdoptionsByList(pids);
        paperAdos = paperAdos.subList(0, total);

        return paperAdos;
    }

    // 返回已认领/未认领的文献
    private List<PaperAdo> getPapersBelonged(String name, boolean isBelonged) {
        List<Map<String, Object>> papers = paperMapper.getPapers();
        List<PaperAdo> paperAdos = new ArrayList<>();

        for (Map<String, Object> paper: papers) {
            if (paper.get("auths") != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    Map<String, Integer> auths = objectMapper.readValue(paper.get("auths").toString(), new TypeReference<Map<String, Integer>>() {
                    });
                    if (auths.get(name) != null) {
                        if (isBelonged && auths.get(name) == 0) {
                            continue;
                        }
                        Date date;
                        // 判断是否是 ISO 格式，转换date格式
                        if (!paper.get("publish_date").toString().contains(" ")) {
                            date = Date.from(LocalDateTime.parse(paper.get("publish_date").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    .atZone(ZoneId.systemDefault()).toInstant());
                        } else {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
                            date = Date.from(LocalDateTime.parse(paper.get("publish_date").toString(), formatter)
                                    .atZone(ZoneId.systemDefault()).toInstant());
                        }
                        PaperAdo paperAdo = new PaperAdo(
                                Long.parseLong(paper.get("pid").toString()),
                                paper.get("title").toString(),
                                auths,
                                date,
                                0,
                                false
                        );
                        paperAdos.add(paperAdo);
                        // 缓存
                        if (isBelonged) {

                        } else {
                            CompletableFuture.runAsync(() -> {
                                redisTool.setExObjectValue("AdoptObject:" + name, paper);    // 认领条目
                            }, taskExecutor);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return paperAdos;
    }
}
