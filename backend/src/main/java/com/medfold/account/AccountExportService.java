package com.medfold.account;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.account.dto.AccountExport;
import com.medfold.common.ApiException;
import com.medfold.document.Document;
import com.medfold.document.DocumentPageRepository;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentTermRepository;
import com.medfold.document.dto.DocumentResponse;
import com.medfold.records.LabResultRepository;
import com.medfold.records.MedicationRepository;
import com.medfold.records.dto.LabResultResponse;
import com.medfold.records.dto.MedicationResponse;
import com.medfold.records.dto.TermResponse;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

/** Builds the user's data export. Every query is scoped to the user. */
@Service
public class AccountExportService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final LabResultRepository labResultRepository;
    private final MedicationRepository medicationRepository;
    private final DocumentTermRepository termRepository;
    private final Clock clock;

    public AccountExportService(UserRepository userRepository, DocumentRepository documentRepository,
            DocumentPageRepository pageRepository, LabResultRepository labResultRepository,
            MedicationRepository medicationRepository, DocumentTermRepository termRepository, Clock clock) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
        this.labResultRepository = labResultRepository;
        this.medicationRepository = medicationRepository;
        this.termRepository = termRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountExport export(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please log in again"));
        List<AccountExport.DocumentExport> documents = documentRepository.findTimeline(userId, Pageable.unpaged())
                .stream().map(document -> exportDocument(userId, document)).toList();
        return new AccountExport(AccountExport.FORMAT, clock.instant(),
                new AccountExport.Account(user.getEmail(), user.getFullName(), user.getCreatedAt()), documents);
    }

    private AccountExport.DocumentExport exportDocument(UUID userId, Document document) {
        UUID id = document.getId();
        return new AccountExport.DocumentExport(
                DocumentResponse.from(document),
                pageRepository.findByDocumentIdAndUserIdOrderByPageNumber(id, userId).stream()
                        .map(p -> new AccountExport.Page(p.getPageNumber(), p.getText(), p.getSource().name(), p.getConfidence()))
                        .toList(),
                labResultRepository.findByDocumentIdAndUserIdOrderByPosition(id, userId).stream()
                        .map(LabResultResponse::from).toList(),
                medicationRepository.findByDocumentIdAndUserIdOrderByPosition(id, userId).stream()
                        .map(MedicationResponse::from).toList(),
                termRepository.findByDocumentIdAndUserIdOrderByPosition(id, userId).stream()
                        .map(TermResponse::from).toList());
    }
}
