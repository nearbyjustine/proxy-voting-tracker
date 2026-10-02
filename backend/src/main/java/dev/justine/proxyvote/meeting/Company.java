package dev.justine.proxyvote.meeting;

import jakarta.persistence.*;

@Entity
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 12)
    private String ticker;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 2)
    private String country;

    protected Company() {}

    public Company(String ticker, String name, String country) {
        this.ticker = ticker;
        this.name = name;
        this.country = country;
    }

    public void rename(String name, String country) {
        this.name = name;
        this.country = country;
    }

    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public String getCountry() { return country; }
}
