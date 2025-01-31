package com.v01.techgear_server.model;

import com.v01.techgear_server.enums.Category;
import com.v01.techgear_server.enums.ProductAvailability;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "product")
public class Product implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long productId;

    @Column(name = "name", unique = true)
    private String name;

    @Column(name = "product_description", unique = true)
    private String productDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_availability")
    private ProductAvailability availability;

    @Column(name="stock_level")
    private int stockLevel;

    @Column(name = "slug", unique = true)
    private String slug;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "image_id")
    private Image image;

    @Column(name = "price")
    private Double price;

    @Column(name = "min_price")
    private Double minPrice;

    @Column(name= "max_price")
    private Double maxPrice;

    @Column(name = "brand")
    private String brand;

    @Enumerated(EnumType.STRING)
    private Category category;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "product")
    @ToString.Exclude
    private List<ProductDetail> productDetail = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "product")
    @ToString.Exclude
    private List<ProductRating> productRatings = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<OrderItems> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<WishlistItems> wishlistItems = new ArrayList<>();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy
                ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Product product = (Product) o;
        return getProductId() != null && Objects.equals(getProductId(), product.getProductId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer()
                                                                       .getPersistentClass()
                                                                       .hashCode() : getClass().hashCode();
    }
}
