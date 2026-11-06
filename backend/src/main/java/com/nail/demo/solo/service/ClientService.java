package com.nail.demo.solo.service;

import java.util.List;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.dto.ClientCreateRequest;
import com.nail.demo.solo.dto.ClientResponse;
import com.nail.demo.solo.dto.ClientUpdateRequest;
import com.nail.demo.solo.entity.Client;
import com.nail.demo.solo.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 客户档案。短号自动生成且永不变更；删除是软删除，历史预约保留。
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    private static final String CODE_PREFIX = "#C";

    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<ClientResponse> search(String keyword) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return clientRepository.search(normalized).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        return toResponse(requireClient(id));
    }

    @Transactional
    public ClientResponse create(ClientCreateRequest request) {
        Client client = new Client();
        client.setCode(nextCode());
        client.setNickname(request.getNickname().trim());
        client.setPhoneTail(normalizePhoneTail(request.getPhoneTail()));
        client.setSource(trimToNull(request.getSource()));
        client.setRemark(trimToNull(request.getRemark()));
        client.setDeleted(0);
        return toResponse(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(Long id, ClientUpdateRequest request) {
        Client client = requireClient(id);
        if (StringUtils.hasText(request.getNickname())) {
            client.setNickname(request.getNickname().trim());
        }
        if (request.getPhoneTail() != null) {
            client.setPhoneTail(normalizePhoneTail(request.getPhoneTail()));
        }
        if (request.getSource() != null) {
            client.setSource(trimToNull(request.getSource()));
        }
        if (request.getRemark() != null) {
            client.setRemark(trimToNull(request.getRemark()));
        }
        return toResponse(clientRepository.save(client));
    }

    /** 软删除：预约、流水都还在，只是客户列表不再出现。 */
    @Transactional
    public void delete(Long id) {
        Client client = requireClient(id);
        client.setDeleted(1);
        clientRepository.save(client);
    }

    /** 下一个短号：#C001、#C002……跳过已被占用的号。 */
    private String nextCode() {
        long seq = clientRepository.findTopByOrderByIdDesc().map(Client::getId).orElse(0L) + 1;
        String code = format(seq);
        while (clientRepository.existsByCode(code)) {
            seq++;
            code = format(seq);
        }
        return code;
    }

    private String format(long seq) {
        return CODE_PREFIX + "%03d".formatted(seq);
    }

    private Client requireClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "客户不存在"));
    }

    private String normalizePhoneTail(String phoneTail) {
        if (!StringUtils.hasText(phoneTail)) {
            return null;
        }
        String trimmed = phoneTail.trim();
        if (!trimmed.matches("\\d{4}")) {
            throw new BusinessException("手机后四位必须是 4 位数字");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ClientResponse toResponse(Client client) {
        return ClientResponse.builder()
                .id(client.getId())
                .code(client.getCode())
                .nickname(client.getNickname())
                .phoneTail(client.getPhoneTail())
                .source(client.getSource())
                .remark(client.getRemark())
                .firstVisitDate(client.getFirstVisitDate())
                .lastVisitDate(client.getLastVisitDate())
                .deleted(client.getDeleted())
                .build();
    }
}
