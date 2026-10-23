package com.example.przetargapp.przetarg;

import com.example.przetargapp.user.Rola;
import com.example.przetargapp.user.User;
import com.example.przetargapp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
public class PrzetargService {
    private final PrzetargRepository przetargRepository;
    private final OfertaRepository ofertaRepository;
    private final UserRepository userRepository;
    private final ZrodloPrzetarguRepository zrodloRepository;
    private final SlowoKluczoweRepository slowoKluczoweRepository;
    private final SilnikKlasyfikacjiPrzetargow silnikKlasyfikacji;
    private final PrzetargHistoriaStatusuRepository historiaRepository;

    @Transactional(readOnly = true)
    public List<PrzetargResponse> listaPrzetargow() {
        return listaPrzetargow(null, null, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<PrzetargResponse> listaPrzetargow(
            StatusOperacyjny status,
            LocalDateTime od,
            LocalDateTime doDaty,
            Integer minPunktacja,
            Integer maxPunktacja,
            UUID zrodloId,
            UUID slowoId,
            String query) {
        if (od != null && doDaty != null && od.isAfter(doDaty)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data początkowa nie może być późniejsza od końcowej");
        }
        if (minPunktacja != null && maxPunktacja != null && minPunktacja > maxPunktacja) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimalna punktacja nie może przekraczać maksymalnej");
        }
        String fraza = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Specification<Przetarg> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("statusOperacyjny"), status));
            }
            if (od != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("dataUtworzenia"), od));
            }
            if (doDaty != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("dataUtworzenia"), doDaty));
            }
            if (minPunktacja != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("punktacja"), minPunktacja));
            }
            if (maxPunktacja != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("punktacja"), maxPunktacja));
            }
            if (zrodloId != null) {
                predicates.add(criteriaBuilder.equal(root.get("zrodlo").get("id"), zrodloId));
            }
            if (slowoId != null) {
                var slowa = root.join("dopasowaneSlowa", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(slowa.get("id"), slowoId));
                criteriaQuery.distinct(true);
            }
            if (fraza != null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("tytul")), fraza),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("opis")), fraza),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("kategoria")), fraza),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lokalizacja")), fraza)));
            }
            criteriaQuery.orderBy(criteriaBuilder.desc(root.get("dataUtworzenia")));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        return przetargRepository.findAll(specification).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OpcjeFiltrowPrzetarguResponse opcjeFiltrowPrzetargu() {
        List<OpcjeFiltrowPrzetarguResponse.OpcjaZrodla> zrodla = zrodloRepository
                .findAllByAktywneTrueOrderByNazwaAsc().stream()
                .map(zrodlo -> new OpcjeFiltrowPrzetarguResponse.OpcjaZrodla(zrodlo.getId(), zrodlo.getNazwa()))
                .toList();
        List<OpcjeFiltrowPrzetarguResponse.OpcjaSlowa> slowa = slowoKluczoweRepository.findAllByAktywneTrue().stream()
                .filter(slowo -> slowo.getTyp() == TypSlowaKluczowego.POSZUKIWANE)
                .map(slowo -> new OpcjeFiltrowPrzetarguResponse.OpcjaSlowa(
                        slowo.getId(), slowo.getFraza(), slowo.getTyp()))
                .toList();
        return new OpcjeFiltrowPrzetarguResponse(zrodla, slowa);
    }

    @Transactional(readOnly = true)
    public PrzetargResponse pobierz(UUID id) {
        return mapToResponse(znajdzPrzetarg(id));
    }

    @Transactional(readOnly = true)
    public List<PrzetargHistoriaStatusuResponse> historiaStatusow(UUID przetargId) {
        znajdzPrzetarg(przetargId);
        return historiaRepository.findByPrzetargIdOrderByDataZmianyDesc(przetargId).stream()
                .map(historia -> new PrzetargHistoriaStatusuResponse(
                        historia.getId(), historia.getPoprzedniStatus(), historia.getNowyStatus(),
                        historia.getZmienilUzytkownik(), historia.getDataZmiany()))
                .toList();
    }

    @Transactional
    public PrzetargResponse utworz(UUID organizatorId, PrzetargRequest request) {
        User organizator = znajdzOrganizatora(organizatorId);
        Przetarg przetarg = Przetarg.builder()
                .tytul(request.tytul().trim())
                .opis(request.opis().trim())
                .kategoria(request.kategoria().trim())
                .lokalizacja(request.lokalizacja().trim())
                .terminSkladaniaOfert(request.terminSkladaniaOfert())
                .organizator(organizator)
                .build();
        przetarg = przetargRepository.save(przetarg);
        silnikKlasyfikacji.klasyfikuj(przetarg, silnikKlasyfikacji.idUzytkownika(organizatorId));
        return mapToResponse(przetargRepository.save(przetarg));
    }

    @Transactional(readOnly = true)
    public List<PrzetargResponse> przetargiOrganizatora(UUID organizatorId) {
        znajdzOrganizatora(organizatorId);
        return przetargRepository.findByOrganizatorIdOrderByDataUtworzeniaDesc(organizatorId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public PrzetargResponse zamknij(UUID przetargId, UUID organizatorId) {
        Przetarg przetarg = znajdzPrzetarg(przetargId);
        if (przetarg.getOrganizator() == null || !przetarg.getOrganizator().getId().equals(organizatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tylko organizator przetargu może go zamknąć");
        }
        przetarg.setStatus(StatusPrzetargu.ZAMKNIETY);
        return mapToResponse(przetarg);
    }

    @Transactional
    public PrzetargResponse zmienStatusOperacyjny(UUID przetargId, StatusOperacyjny status, UUID requesterId) {
        Przetarg przetarg = znajdzPrzetarg(przetargId);
        przetarg.setStatusUstawionyRecznie(true);
        silnikKlasyfikacji.zmienStatus(przetarg, status, silnikKlasyfikacji.idUzytkownika(requesterId));
        return mapToResponse(przetargRepository.save(przetarg));
    }

    @Transactional
    public WynikImportuResponse importuj(ImportPrzetarguRequest request) {
        ZrodloPrzetargu zrodlo = zrodloRepository.findById(request.zrodloId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono źródła"));
        if (!zrodlo.isAktywne()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Źródło jest nieaktywne");
        }

        Przetarg przetarg = przetargRepository
                .findByZrodloIdAndZewnetrzneId(request.zrodloId(), request.zewnetrzneId().trim())
                .orElse(null);
        boolean nowy = przetarg == null;
        if (nowy) {
            przetarg = Przetarg.builder().zrodlo(zrodlo).zewnetrzneId(request.zewnetrzneId().trim()).build();
        }
        przetarg.setTytul(request.tytul().trim());
        przetarg.setOpis(request.opis().trim());
        przetarg.setKategoria(request.kategoria().trim());
        przetarg.setLokalizacja(request.lokalizacja().trim());
        przetarg.setTerminSkladaniaOfert(request.terminSkladaniaOfert());
        przetarg.setAdresZrodla(request.adresZrodla() == null ? null : request.adresZrodla().trim());
        przetarg = przetargRepository.save(przetarg);
        silnikKlasyfikacji.klasyfikuj(przetarg, silnikKlasyfikacji.uzytkownikSystemowy());
        przetarg = przetargRepository.save(przetarg);
        return new WynikImportuResponse(
                przetarg.getId(), nowy, przetarg.getStatusOperacyjny(), przetarg.getPunktacja());
    }

    @Transactional
    public OfertaResponse zlozOferte(UUID przetargId, UUID wykonawcaId, OfertaRequest request) {
        Przetarg przetarg = znajdzPrzetarg(przetargId);
        if (przetarg.getStatus() != StatusPrzetargu.OTWARTY
                || !przetarg.getTerminSkladaniaOfert().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Termin składania ofert minął lub przetarg jest zamknięty");
        }
        User wykonawca = userRepository.findById(wykonawcaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono użytkownika"));
        if (wykonawca.getRola() != Rola.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organizator nie może składać ofert");
        }
        if (przetarg.getOrganizator() != null && przetarg.getOrganizator().getId().equals(wykonawcaId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organizator nie może składać oferty we własnym przetargu");
        }
        if (ofertaRepository.existsByPrzetargIdAndWykonawcaId(przetargId, wykonawcaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Złożono już ofertę w tym przetargu");
        }

        Oferta oferta = Oferta.builder()
                .przetarg(przetarg)
                .wykonawca(wykonawca)
                .cena(request.cena())
                .opis(request.opis().trim())
                .build();
        return mapToResponse(ofertaRepository.save(oferta));
    }

    @Transactional(readOnly = true)
    public List<OfertaResponse> ofertyUzytkownika(UUID wykonawcaId) {
        userRepository.findById(wykonawcaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono użytkownika"));
        return ofertaRepository.findByWykonawcaIdOrderByDataZlozeniaDesc(wykonawcaId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private User znajdzOrganizatora(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono użytkownika"));
        if (user.getRola() != Rola.ORGANIZER && user.getRola() != Rola.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Konto nie ma uprawnień organizatora");
        }
        return user;
    }

    private Przetarg znajdzPrzetarg(UUID id) {
        return przetargRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono przetargu"));
    }

    private PrzetargResponse mapToResponse(Przetarg przetarg) {
        String status = przetarg.getStatus() == StatusPrzetargu.ZAMKNIETY
                ? "ZAMKNIETY"
                : przetarg.getTerminSkladaniaOfert().isAfter(LocalDateTime.now()) ? "OTWARTY" : "ZAKONCZONY";
        return new PrzetargResponse(
                przetarg.getId(),
                przetarg.getTytul(),
                przetarg.getOpis(),
                przetarg.getKategoria(),
                przetarg.getLokalizacja(),
                przetarg.getTerminSkladaniaOfert(),
                status,
                przetarg.getDataUtworzenia(),
                przetarg.getOrganizator() == null ? null : przetarg.getOrganizator().getId(),
                przetarg.getOrganizator() == null
                        ? "Źródło: " + (przetarg.getZrodlo() == null ? "zewnętrzne" : przetarg.getZrodlo().getNazwa())
                        : przetarg.getOrganizator().getImie() + " " + przetarg.getOrganizator().getNazwisko(),
                ofertaRepository.countByPrzetargId(przetarg.getId()),
                przetarg.getStatusOperacyjny(),
                przetarg.getPunktacja(),
                przetarg.getZrodlo() == null ? null : przetarg.getZrodlo().getId(),
                przetarg.getZrodlo() == null ? null : przetarg.getZrodlo().getNazwa(),
                przetarg.getDopasowaneSlowa().stream().map(SlowoKluczowe::getFraza).sorted().toList()
        );
    }

    private OfertaResponse mapToResponse(Oferta oferta) {
        return new OfertaResponse(
                oferta.getId(),
                oferta.getPrzetarg().getId(),
                oferta.getPrzetarg().getTytul(),
                oferta.getCena(),
                oferta.getOpis(),
                oferta.getDataZlozenia()
        );
    }
}
