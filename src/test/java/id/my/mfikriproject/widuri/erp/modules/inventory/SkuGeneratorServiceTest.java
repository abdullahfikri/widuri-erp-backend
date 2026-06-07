package id.my.mfikriproject.widuri.erp.modules.inventory;

import id.my.mfikriproject.widuri.erp.modules.inventory.repository.SkuRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.impl.SkuGeneratorServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkuGeneratorServiceTest {

    @Mock
    private SkuRepository skuRepository;

    @InjectMocks
    private SkuGeneratorServiceImpl service;

    @Test
    void generate_cleanInputs_returnsCorrectFormat() {
        given(skuRepository.getNextSkuSequence()).willReturn("001");

        String result = service.generate("Shimano", "Reel", "Silver");

        assertThat(result).isEqualTo("SHIMANO-REEL-SILVER-001");
    }

    @Test
    void generate_lowercaseInputs_normalizesToUppercase() {
        given(skuRepository.getNextSkuSequence()).willReturn("042");

        String result = service.generate("shimano", "reel", "silver");

        assertThat(result).isEqualTo("SHIMANO-REEL-SILVER-042");
    }

    @Test
    void generate_inputsWithSpaces_replacesSpacesWithDashes() {
        given(skuRepository.getNextSkuSequence()).willReturn("007");

        String result = service.generate("Daiwa Japan", "Spinning Reel", "Deep Blue");

        assertThat(result).isEqualTo("DAIWA-JAPAN-SPINNING-REEL-DEEP-BLUE-007");
    }

    @Test
    void generate_inputsWithLeadingTrailingWhitespace_trimsCorrectly() {
        given(skuRepository.getNextSkuSequence()).willReturn("010");

        String result = service.generate("  Shimano  ", " Reel ", " Silver ");

        assertThat(result).isEqualTo("SHIMANO-REEL-SILVER-010");
    }

    @Test
    void generate_largeSequenceNumber_padsToThreeDigits() {
        given(skuRepository.getNextSkuSequence()).willReturn("999");

        String result = service.generate("Brand", "Cat", "Attr");

        assertThat(result).isEqualTo("BRAND-CAT-ATTR-999");
    }

    @Test
    void generate_sequenceReturnsMinimumValue_uses001() {
        given(skuRepository.getNextSkuSequence()).willReturn("001");

        String result = service.generate("Brand", "Cat", "Attr");

        assertThat(result).isEqualTo("BRAND-CAT-ATTR-001");
    }

    @Test
    void generate_brandIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.generate(null, "Reel", "Silver"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_categoryIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.generate("Shimano", null, "Silver"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_attributeIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.generate("Shimano", "Reel", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_brandIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.generate("   ", "Reel", "Silver"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_categoryIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.generate("Shimano", "", "Silver"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_attributeIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.generate("Shimano", "Reel", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_categoryWithMultipleConsecutiveSpaces_collapsesToSingleDash() {
        given(skuRepository.getNextSkuSequence()).willReturn("001");

        String result = service.generate("Shimano", "Spinning  Reel", "Silver");

        assertThat(result).isEqualTo("SHIMANO-SPINNING-REEL-SILVER-001");
    }

    @Test
    void generate_delegatesToRepository() {
        given(skuRepository.getNextSkuSequence()).willReturn("005");

        service.generate("Brand", "Cat", "Attr");

        verify(skuRepository).getNextSkuSequence();
    }
}
