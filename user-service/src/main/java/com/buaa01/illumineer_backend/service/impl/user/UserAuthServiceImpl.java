package com.buaa01.illumineer_backend.service.impl.user;

import com.buaa01.illumineer_backend.entity.CustomResponse;
import com.buaa01.illumineer_backend.entity.User;
import com.buaa01.illumineer_backend.service.user.UserAuthService;
import com.buaa01.illumineer_backend.service.utils.CurrentUser;
import com.buaa01.illumineer_backend.tool.RedisTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserAuthServiceImpl implements UserAuthService {

    @Autowired
    private CurrentUser currentUser;
    @Autowired
    private RedisTool redisTool;



    /**
     * 按照无序集合查询
     * 同时维护两个set
     * paperBelonged : 拥有改文章的作者的uid集合
     * property : 某个作者拥有文章的pid集合
     * **/
    @Override
    public CustomResponse claim(Integer add , List<Integer> pids){
        CustomResponse customResponse = new CustomResponse();
        User user = currentUser.getUser();
        //初始化
        customResponse.setCode(200);
        customResponse.setMessage("论文认领成功");
        String fidKey = "property:" + user.getUid();
        //添加文章
        if(add==1) {
            for (Integer pid : pids) {
                if (!redisTool.isExist(fidKey)) {
                    customResponse.setCode(500);
                    customResponse.setMessage("Redis中该用户实名下的论文集合未创建，可能未在实名过程中调用创建函数");
                }
                //fid下已经有该论文
                else if (redisTool.isSetMember(fidKey, pid)) {
                }
                //收藏论文
                else {
                    redisTool.addSetMember(fidKey, pid);
                }
            }
        }else {
            for (Integer pid : pids) {
                if (!redisTool.isExist(fidKey)) {
                    customResponse.setCode(500);
                    customResponse.setMessage("Redis中该用户实名下的论文集合未创建，可能未在实名过程中调用创建函数");
                }
                //fid下已经有该论文
                else if (!redisTool.isSetMember(fidKey, pid)) {
                    customResponse.setCode(500);
                    customResponse.setMessage("尝试删除本不存在于集合中的论文");
                }
                //收藏论文
                else {
                    //可以使用这个函数吗？
                    redisTool.addSetMember(fidKey, pid);
                }
            }
        }
        return customResponse;
    }


}
