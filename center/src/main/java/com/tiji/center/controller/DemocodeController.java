package com.tiji.center.controller;

import com.tiji.center.pojo.Democode;
import com.tiji.center.pojo.Vuln;
import com.tiji.center.service.DemocodeService;
import com.tiji.center.service.VulnService;
import entity.PageResult;
import entity.Result;
import entity.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * democode控制器层
 *
 * @author 贰拾壹
 */
@RestController
@CrossOrigin
@RequestMapping("/democode")
public class DemocodeController {

    @Autowired
    private DemocodeService democodeService;

    @Autowired
    private VulnService vulnService;

    /**
     * 查询全部数据
     *
     * @return
     */
    @GetMapping
    public Result findAll() {
        return new Result(true, StatusCode.OK, "查询成功", democodeService.findAll());
    }

    /**
     * 根据ID查询
     *
     * @param id ID
     * @return
     */
    @GetMapping(value = "/{id}")
    public Result findById(@PathVariable String id) {
        return new Result(true, StatusCode.OK, "查询成功", democodeService.findById(id));
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
        Page<Democode> pageList = democodeService.findSearch(searchMap, page, size);
        pageList.stream().parallel().forEach(democode -> {
            String vulnid = democode.getVulnid();
            if (!StringUtils.isEmpty(vulnid)) {
                Vuln vuln = vulnService.findById(vulnid);
                if(!Objects.isNull(vuln)){
                    democode.setVulnid(vuln.getName());
                }
            }
        });
        return new Result(true, StatusCode.OK, "查询成功", new PageResult<Democode>(pageList.getTotalElements(), pageList.getContent()));
    }

    /**
     * 根据条件查询
     *
     * @param searchMap
     * @return
     */
    @PostMapping(value = "/search")
    public Result findSearch(@RequestBody Map searchMap) {
        return new Result(true, StatusCode.OK, "查询成功", democodeService.findSearch(searchMap));
    }

    /**
     * 增加
     *
     * @param democode
     */
    @PostMapping
    public Result add(@RequestBody Democode democode) {
        democodeService.add(democode);
        return new Result(true, StatusCode.OK, "增加成功");
    }

    /**
     * 修改
     *
     * @param democode
     */
    @PutMapping(value = "/{id}")
    public Result update(@RequestBody Democode democode, @PathVariable String id) {
        democode.setId(id);
        democodeService.update(democode);
        return new Result(true, StatusCode.OK, "修改成功");
    }

    /**
     * 删除
     *
     * @param id
     */
    @DeleteMapping(value = "/{id}")
    public Result delete(@PathVariable String id) {
        democodeService.deleteById(id);
        return new Result(true, StatusCode.OK, "删除成功");
    }

    /**
     * 根据ids批量删除
     *
     * @param ids
     */
    @PostMapping(value = "/deleteids")
    public Result deleteAllByIds(@RequestBody List<String> ids) {
        democodeService.deleteAllByIds(ids);
        return new Result(true, StatusCode.OK, "删除成功");
    }


    /**
     * 根据vulnId查询
     *
     * @param vulnId vulnId
     * @return
     */
    @RequestMapping(value = "/vuln/{vulnId}", method = RequestMethod.GET)
    public Result findAllByVulnId(@PathVariable String vulnId) {
        return new Result(true, StatusCode.OK, "查询成功", democodeService.findAllByVulnId(vulnId));
    }


}
