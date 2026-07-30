package com.neviswealth.searchapi.seed;

import com.neviswealth.searchapi.client.Client;
import com.neviswealth.searchapi.client.ClientRepository;
import com.neviswealth.searchapi.client.ClientService;
import com.neviswealth.searchapi.client.dto.CreateClientRequest;
import com.neviswealth.searchapi.document.dto.CreateDocumentRequest;
import com.neviswealth.searchapi.document.DocumentService;
import com.neviswealth.searchapi.elasticsearch.ElasticsearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds demo clients and documents on startup if the database is empty. Idempotent.
 * Disabled under the {@code test} profile so integration tests control their own data.
 */
@Component
@Profile("!test")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ClientRepository clients;
    private final ClientService clientService;
    private final DocumentService documentService;
    private final ElasticsearchIndexService esIndexService;

    public DataSeeder(ClientRepository clients, ClientService clientService,
                      DocumentService documentService,
                      ElasticsearchIndexService esIndexService) {
        this.clients = clients;
        this.clientService = clientService;
        this.documentService = documentService;
        this.esIndexService = esIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (clients.count() > 0) {
            log.info("Seed skipped: {} client(s) already present", clients.count());
            esIndexService.reindexAll();  // restart case — DB has data, sync ES
            return;
        }
        log.info("Seeding demo data (empty database detected)…");

        // Client 1
        Client john = clientService.create(new CreateClientRequest(
                "John", "Doe", "john.doe@neviswealth.com",
                "Long-standing advisory client; retirement and estate planning.",
                List.of("https://linkedin.com/in/johndoe")));
        documentService.create(john.getId(), new CreateDocumentRequest(
                "Electricity utility bill — March 2026",
                "Monthly electricity utility bill. Account holder: John Doe. Service address: "
                        + "123 Main Street, Springfield. Amount due: $84.20. This statement can be "
                        + "used as proof of residence and address for verification purposes."));
        documentService.create(john.getId(), new CreateDocumentRequest(
                "Investment portfolio summary — Q1 2026",
                "Quarterly investment portfolio summary. Equity holdings, bond allocations, and "
                        + "dividend income for the period. Total portfolio value $1.2M."));

        // Client 2
        Client maria = clientService.create(new CreateClientRequest(
                "Maria", "Garcia", "maria.garcia@example.com",
                "New client onboarded 2026; completing KYC.",
                List.of()));
        documentService.create(maria.getId(), new CreateDocumentRequest(
                "Passport copy",
                "Certified copy of passport for identity verification. Full name Maria Garcia, "
                        + "nationality Spanish, document used for KYC onboarding."));
        documentService.create(maria.getId(), new CreateDocumentRequest(
                "Bank statement — February 2026",
                "Monthly current-account bank statement. Opening balance $5,000, closing balance "
                        + "$6,420. Salary credit and standard living expenses."));

        // Client 3
        Client david = clientService.create(new CreateClientRequest(
                "David", "Chen", "david.chen@wealthmail.com",
                "High-net-worth client; interested in tax-efficient investing.",
                List.of("https://twitter.com/davidchen")));
        documentService.create(david.getId(), new CreateDocumentRequest(
                "Council tax bill",
                "Annual council tax bill for the residential property. Serves as confirmation of "
                        + "the resident's home address for the tax year."));
        documentService.create(david.getId(), new CreateDocumentRequest(
                "Investment advisory letter — Q1 2026",
                "Dear David,\n\n"
                        + "Following our meeting on 15 January 2026, I am writing to summarise the agreed "
                        + "changes to your investment portfolio and outline the rationale behind each "
                        + "recommendation.\n\n"
                        + "Current portfolio value: £4,250,000 as of 31 March 2026. Asset allocation stands "
                        + "at 62% equities, 25% fixed income, 8% alternatives, and 5% cash. Over the past "
                        + "quarter the portfolio returned +3.8%, outperforming the benchmark by 0.6%.\n\n"
                        + "Recommendation 1 — Reduce UK equity exposure by 5% (from 30% to 25%) and "
                        + "reallocate to international developed markets. The rationale is to reduce "
                        + "concentration risk given ongoing domestic policy uncertainty and to capture "
                        + "stronger earnings growth in US and European markets.\n\n"
                        + "Recommendation 2 — Increase allocation to short-duration investment-grade bonds "
                        + "by 3%. With central banks signalling a pause in rate hikes, shorter-duration "
                        + "bonds offer attractive yields (4.2–4.8% annualised) with limited interest-rate "
                        + "sensitivity.\n\n"
                        + "Recommendation 3 — Introduce a 2% allocation to infrastructure funds. These "
                        + "provide inflation-linked returns and low correlation to public equities, "
                        + "improving portfolio diversification.\n\n"
                        + "Tax considerations: The equity sales will crystallise approximately £18,000 in "
                        + "capital gains. This falls within your remaining annual CGT allowance (£6,000) "
                        + "plus losses carried forward from 2024 (£14,200), so no tax liability arises.\n\n"
                        + "Next steps: Please confirm your agreement to the above changes by replying to "
                        + "this letter or contacting the office. Once confirmed, trades will be executed "
                        + "within 5 business days at prevailing market prices.\n\n"
                        + "Kind regards,\n"
                        + "Sarah Thompson\n"
                        + "Senior Investment Advisor\n"
                        + "Nevis Wealth Management"));

        log.info("Seed complete: {} clients created", clients.count());
        esIndexService.reindexAll();  // first boot case — seed done, now sync ES
    }
}