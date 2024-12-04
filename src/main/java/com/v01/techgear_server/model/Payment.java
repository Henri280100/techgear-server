package com.v01.techgear_server.model;

import java.util.*;

import com.v01.techgear_server.enums.PaymentStatus;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Data
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long paymentId;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Invoice invoice;

    @Column(name = "payment_amount", columnDefinition = "DECIMAL(10,2)")
    private BigDecimal paymentAmount;

    @Column(name="payment_date")
    private LocalDateTime paymentDate;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PaymentLogs> paymentsLogs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_details_id", referencedColumnName = "accountDetailsId")
    private AccountDetails accountDetails;
}
