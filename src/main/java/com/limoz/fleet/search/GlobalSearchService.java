package com.limoz.fleet.search;

import com.limoz.fleet.search.dto.GlobalSearchResponse;
import com.limoz.fleet.search.dto.SearchResult;
import com.limoz.fleet.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final List<SearchSource> sources;

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String query, int limitPerGroup) {
        String q = query == null ? "" : query.trim();
        Map<String, List<SearchResult>> groups = new LinkedHashMap<>();
        int total = 0;
        if (q.length() >= 2) {
            for (SearchSource source : sources) {
                if (!SecurityUtils.hasPermission(source.requiredPermission())) continue;
                List<SearchResult> hits = source.search(q, limitPerGroup);
                if (!hits.isEmpty()) {
                    groups.put(source.group(), hits);
                    total += hits.size();
                }
            }
        }
        return new GlobalSearchResponse(q, total, groups);
    }
}
