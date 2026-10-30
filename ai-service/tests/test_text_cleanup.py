from app.ocr.text_cleanup import clean_text, meaningful_char_count


def test_keeps_column_gaps_but_shortens_them():
    assert clean_text("HbA1c          6.1        %") == "HbA1c   6.1   %"


def test_removes_trailing_spaces_control_chars_and_extra_blank_lines():
    assert clean_text("Line one   \r\n\x00\n\n\n\nLine two\x0c") == "Line one\n\nLine two"


def test_normalizes_unicode_to_composed_form():
    assert clean_text("café") == "café"


def test_meaningful_chars_ignore_whitespace_and_symbols():
    assert meaningful_char_count("  -- 12 ab --  \n") == 4
