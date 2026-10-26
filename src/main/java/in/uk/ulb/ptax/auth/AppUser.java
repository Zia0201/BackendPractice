package in.uk.ulb.ptax.auth;

import in.uk.ulb.ptax.common.Role;
import in.uk.ulb.ptax.ulb.Ward;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String passwordHash;
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Long ulbId;
    private String mobile;
    private String email;
    private boolean active;

    @ManyToMany
    @JoinTable(name = "user_ward",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "ward_id"))
    private Set<Ward> wards = new HashSet<>();
}
