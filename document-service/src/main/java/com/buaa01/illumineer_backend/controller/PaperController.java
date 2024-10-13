package com.buaa01.illumineer_backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.buaa01.illumineer_backend.entity.CustomResponse;
import com.buaa01.illumineer_backend.entity.Paper;
import com.buaa01.illumineer_backend.mapper.PaperMapper;
import com.buaa01.illumineer_backend.service.paper.PaperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class PaperController {

    @Autowired
    private PaperService paperService;

    /**
     * 根据pid获取文献信息
     * @param pid 文献ID
     * @return 文献信息
     */
    @GetMapping("/paper/get")
    public CustomResponse getPaperByPid(@RequestParam("pid") Integer pid) {
        try {
            return paperService.getPaperByPid(pid);
        } catch (Exception e) {
            e.printStackTrace();
            CustomResponse customResponse = new CustomResponse();
            customResponse.setCode(500);
            customResponse.setMessage("无法根据pid获取文献信息！");
            return customResponse;
        }
    }
}
