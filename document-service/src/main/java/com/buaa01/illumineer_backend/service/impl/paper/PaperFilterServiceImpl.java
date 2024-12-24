package com.buaa01.illumineer_backend.service.impl.paper;

import com.buaa01.illumineer_backend.entity.CustomResponse;
import com.buaa01.illumineer_backend.entity.SearchResultPaper;
import com.buaa01.illumineer_backend.service.paper.PaperFilterService;
import com.buaa01.illumineer_backend.tool.RedisTool;
import com.buaa01.illumineer_backend.utils.FilterCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class PaperFilterServiceImpl implements PaperFilterService {

    @Autowired
    private PaperSearchServiceImpl paperSearchServiceImpl;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Autowired
    private RedisTool redisTool;

    @Override
    public Map<String, Object> filterSearchResult(FilterCondition sc, Integer size, Integer offset,
            Integer sortType, Integer order) {

        boolean isYearEmpty = sc.getYear().isEmpty();
        boolean isDerivationEmpty = sc.getDerivation().isEmpty();
        boolean isTypeEmpty = sc.getType().isEmpty();
        boolean isThemeEmpty = sc.getTheme().isEmpty();

        Set<String> filterYears = isYearEmpty ? Collections.emptySet() : new HashSet<>(sc.getYear());
        Set<String> filterDerivations = isDerivationEmpty ? Collections.emptySet() : new HashSet<>(sc.getDerivation());
        Set<String> filterTypes = isTypeEmpty ? Collections.emptySet() : new HashSet<>(sc.getType());
        Set<String> filterThemes = isThemeEmpty ? Collections.emptySet() : new HashSet<>(sc.getTheme());

        List<SearchResultPaper> filteredPapers = paperSearchServiceImpl.getFromRedis().parallelStream()
                .filter(paper -> {
                    Integer PublishYear = paper.getPublishDate().getYear() + 1900;
                    boolean matchesYear = isYearEmpty || filterYears.contains(PublishYear.toString());
                    if (!matchesYear)
                        return false; // 年份不匹配

                    boolean matchesDerivation = isDerivationEmpty || filterDerivations.contains(paper.getDerivation());
                    if (!matchesDerivation)
                        return false; // 来源不匹配

                    boolean matchesType = isTypeEmpty || filterTypes.contains(paper.getType());
                    if (!matchesType)
                        return false; // 类型不匹配

                    boolean matchesTheme = isThemeEmpty || filterThemes.contains(paper.getTheme());
                    if (!matchesTheme)
                        return false; // 主题不匹配

                    return true;
                })
                .collect(Collectors.toList());

        List<SearchResultPaper> sortedPapers = sortPapers(filteredPapers, sortType, order);
        List<SearchResultPaper> resultPapers = paperSearchServiceImpl.searchByPage(sortedPapers, size, offset);

        HashMap<String, Object> returnValues = new HashMap<>();
        returnValues.put("resultPapers", resultPapers);
        returnValues.put("total", sortedPapers.size());
        return returnValues;
    }

    List<SearchResultPaper> sortPapers(List<SearchResultPaper> papers, Integer sortType, Integer order) {
        Comparator<SearchResultPaper> comparator;

        if (sortType == 1) {
            comparator = Comparator.comparing(SearchResultPaper::getPublishDate);
        } else if (sortType == 2) {
            comparator = Comparator.comparingInt(SearchResultPaper::getRefTimes);
        } else if (sortType == 3) {
            comparator = Comparator.comparingInt(SearchResultPaper::getFavTime);
        } else {
            return papers; // 如果没有匹配的排序类型，直接返回原始列表
        }

        if (order == 0) { // 降序
            comparator = comparator.reversed();
        }

        SearchResultPaper[] paperArray = papers.toArray(new SearchResultPaper[0]);
        Arrays.parallelSort(paperArray, comparator);

        return Arrays.asList(paperArray);

        // return papers.parallelStream()
        // .sorted(comparator)
        // .collect(Collectors.toList());
    }

    /*
     * ========== 缓存操作 ==========
     */

    // 将查询结果存到 redis 中
    public void saveToRedis(List<SearchResultPaper> searchResultPapers) {
        for (SearchResultPaper paper : searchResultPapers) {
            CompletableFuture.runAsync(() -> {
                redisTool.setExObjectValue("FilterPaper" + paper.getPid(), paper); // 异步更新到redis
            }, taskExecutor);
        }
    }

    // // 从 redis 中获取暂存信息
    // public List<SearchResultPaper> getFromRedis() {
    // List<SearchResultPaper> papers = new ArrayList<>();
    // Set<String> keySet = redisTool.getKeysByPrefix("paper");

    // List<CompletableFuture<Void>> futures = new ArrayList<>();

    // for (String key : keySet) {
    // CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {// 异步执行任务
    // SearchResultPaper paper = redisTool.getObjectByClass(key,
    // SearchResultPaper.class);
    // synchronized (papers) { // 确保线程安全
    // papers.add(paper);
    // }
    // }, taskExecutor);
    // futures.add(future);
    // }
    // CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    // return papers;
    // }

    // // 删除 redis 中的信息
    // public void deleteFromRedis() {
    // redisTool.deleteByPrefix("paper");
    // }

}
