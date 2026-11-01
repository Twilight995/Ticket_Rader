package com.wol32.ticketradar.extract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 절대 규칙 6: AI에 보내기 전에 연락처를 지운다. 날짜·가격·좌석은 지우지 않는다. 가상 번호(010-0000-xxxx)만 쓴다. */
class ContactMaskerTest {

    @Test
    void masks_phone_numbers_in_common_forms() {
        String text = "연락 010-0000-1234 또는 010 0000 5678, 01000009999, +82-10-0000-1111 로";

        String masked = ContactMasker.mask(text);

        assertThat(masked).doesNotContain("0000-1234", "0000 5678", "01000009999", "0000-1111");
        assertThat(masked).contains("[삭제]");
    }

    @Test
    void masks_bank_accounts_open_chat_telegram_email_and_messenger_ids() {
        String text = "입금 국민 123456-04-123456, 신한 110-123-456789 / 오픈채팅 https://open.kakao.com/o/abcd1234 "
                + "/ 텔레그램 @ticket_seller99 t.me/seller_x / 메일 seller@example.com / 카톡 아이디 tiktok_seller";

        String masked = ContactMasker.mask(text);

        assertThat(masked).doesNotContain("123456-04-123456", "110-123-456789", "open.kakao.com", "abcd1234",
                "@ticket_seller99", "t.me/seller_x", "seller@example.com", "tiktok_seller");
        assertThat(masked).contains("입금", "[삭제]");
    }

    @Test
    void keeps_prices_dates_seats_and_quantities() {
        String text = "2026-12-20 공연 VIP석 A구역 12열 2장, 장당 150,000원 (총 300000원) 양도. 12/20 19시 입장";

        String masked = ContactMasker.mask(text);

        assertThat(masked).isEqualTo(text);
    }

    @Test
    void masks_area_codes_spaced_hyphens_fullwidth_digits_and_ids_without_the_word_id() {
        String text = "02-123-4567 / 010 - 1234 - 5678 / ０１０-0000-1234 / 카톡 abc123 / 오픈카톡 abc / 인스타 abc_1 "
                + "/ 텔레그램 아이디 abc_1 / https://www.instagram.com/foo / 계좌 3333 01 1234567";

        String masked = ContactMasker.mask(text);

        assertThat(masked).doesNotContain("123-4567", "1234 - 5678", "0000-1234", "abc123", "오픈카톡 abc", "abc_1",
                "instagram.com/foo", "3333 01 1234567");
    }

    @Test
    void keeps_times_and_hotline_numbers() {
        String text = "18:00~21:00 입장, 문의 1588-1234, 2026.12.20 19:00";

        assertThat(ContactMasker.mask(text)).isEqualTo(text);
    }

    @Test
    void handles_null_and_blank() {
        assertThat(ContactMasker.mask(null)).isNull();
        assertThat(ContactMasker.mask("  ")).isEqualTo("  ");
    }
}
