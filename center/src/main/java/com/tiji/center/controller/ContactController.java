package com.tiji.center.controller;

import com.tiji.center.pojo.Contact;
import com.tiji.center.pojo.ContactProjectinfo;
import com.tiji.center.service.ContactProjectinfoService;
import com.tiji.center.service.ContactService;
import entity.PageResult;
import entity.Result;
import entity.StatusCode;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import util.IdWorker;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * contact控制器层
 *
 * @author 贰拾壹
 */
@RestController
@CrossOrigin
@RequestMapping("/contact")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @Autowired
    private ContactProjectinfoService contactProjectinfoService;


    /**
     * 查询全部数据
     *
     * @return
     */
    @GetMapping
    public Result findAll() {
        return new Result(true, StatusCode.OK, "查询成功", contactService.findAll());
    }

    /**
     * 根据ID查询
     *
     * @param id ID
     * @return
     */
    @GetMapping(value = "/{id}")
    public Result findById(@PathVariable String id) {
        return new Result(true, StatusCode.OK, "查询成功", contactService.findById(id));
    }


    /**
     * 分页+多条件查询
     *
     * @param searchMap 查询条件封装
     * @param page      页码
     * @param size      页大小
     * @return 分页结果
     */
     @PostMapping(value = "/search/{page}/{size}")
    public Result findSearch(@RequestBody Map searchMap, @PathVariable int page, @PathVariable int size) {
        Page<Contact> pageList = contactService.findSearch(searchMap, page, size);
        return new Result(true, StatusCode.OK, "查询成功", new PageResult<Contact>(pageList.getTotalElements(), pageList.getContent()));
    }

    /**
     * 根据条件查询
     *
     * @param searchMap
     * @return
     */
    @PostMapping(value = "/search")
    public Result findSearch(@RequestBody Map searchMap) {
        return new Result(true, StatusCode.OK, "查询成功", contactService.findSearch(searchMap));
    }

    /**
     * 增加
     *
     * @param contact
     */
    @PostMapping
    public Result add(@RequestBody Contact contact) {
        Contact contactInDb = contactService.findByNameAndEmail(contact.getName(), contact.getEmail());
        if (Objects.isNull(contactInDb)) {
            contactService.add(contact);
            return new Result(true, StatusCode.OK, "增加成功");
        } else {
            return new Result(false, StatusCode.ERROR, "增加失败：联系人和邮箱重复");
        }
    }

    /**
     * 修改
     *
     * @param contact
     */
    @PutMapping(value = "/{id}")
    public Result update(@RequestBody Contact contact, @PathVariable String id) {
        contact.setId(id);
        contactService.update(contact);
        return new Result(true, StatusCode.OK, "修改成功");
    }

    /**
     * 删除
     *
     * @param id
     */
    @DeleteMapping(value = "/{id}")
    public Result delete(@PathVariable String id) {
        contactService.deleteById(id);
        //删除联系人的同时，删除与项目信息关联
        contactProjectinfoService.deleteAllByContactid(id);
        return new Result(true, StatusCode.OK, "删除成功");
    }


    /**
     * 根据ids批量删除
     *
     * @param ids
     */
    @PostMapping(value = "/deleteids")
    public Result deleteAllByIds(@RequestBody List<String> ids) {
        contactService.deleteAllByIds(ids);
        ids.forEach(id -> {
            //删除联系人的同时，删除与项目信息关联
            contactProjectinfoService.deleteAllByContactid(id);
        });
        return new Result(true, StatusCode.OK, "删除成功");
    }
}
