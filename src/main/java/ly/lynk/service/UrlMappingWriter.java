package ly.lynk.service;

import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists URL mappings in a dedicated transaction.
 *
 * <p>Inserting in an isolated transaction lets the caller observe a
 * {@code DataIntegrityViolationException} from a unique-alias collision and retry with a different
 * alias without the failed insert poisoning the surrounding transaction.
 */
@Component
public class UrlMappingWriter {

    private final UrlMappingRepository repository;

    public UrlMappingWriter(UrlMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UrlMapping save(UrlMapping mapping) {
        return repository.saveAndFlush(mapping);
    }
}
