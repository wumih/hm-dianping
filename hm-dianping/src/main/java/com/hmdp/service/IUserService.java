package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session HttpSession
     * @return Result
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @param session HttpSession
     * @return Result
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 用户签到
     * @return Result
     */
    Result sign();

    /**
     * 统计连续签到天数
     * @return Result
     */
    Result signCount();

    /**
     * 登出功能
     * @param token 用户的token
     * @return Result
     */
    Result logout(String token);

    /**
     * 设置/修改密码
     * @param phone 手机号（用于定位用户）
     * @param newPassword 明文新密码
     * @return Result
     */
    Result setPassword(String phone, String newPassword);

    /**
     * 修改用户基础资料（昵称、头像）
     * @param user 只需包含 nickName 和 icon 字段
     * @param token 当前登录的 Token，用于同步更新 Redis
     */
    Result updateMe(User user, String token);

    /**
     * 修改用户详细资料（城市、个人介绍、性别、生日）
     * @param userInfo UserInfo 对象（不需要传 userId，内部自动从 ThreadLocal 取）
     */
    Result updateUserInfo(UserInfo userInfo);

}
