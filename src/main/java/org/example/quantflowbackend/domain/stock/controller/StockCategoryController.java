package org.example.quantflowbackend.domain.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.stock.service.StockCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StockCategoryController {
    private final StockCategoryService categories;

    @GetMapping
    @Operation(summary = "관심 카테고리 목록", description = "토스 산업 분류의 카테고리 코드와 계층을 반환합니다.")
    public List<CategoryResponse> list() {
        return categories.list().stream().map(category ->
                new CategoryResponse(category.code(), category.name(), category.depth(), category.parentCode())
        ).toList();
    }

    public record CategoryResponse(String code, String name, int depth, String parentCode) {}
}
