package cn.nukkit.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemBookPageStateTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 25, 49})
    void insertingIntoFullBookPreservesEverySavedPage(int insertionIndex) {
        ItemBookAndQuill book = new ItemBookAndQuill();
        List<String> expected = new ArrayList<>();
        for (int page = 0; page < 50; page++) {
            String text = "Existing page " + page;
            assertTrue(book.setPageText(page, text));
            expected.add(text);
        }
        byte[] savedBefore = book.getCompoundTag().clone();

        assertFalse(book.insertPage(insertionIndex, "New text"));

        assertEquals(50, book.getPages().size());
        assertArrayEquals(savedBefore, book.getCompoundTag());
        ItemBookAndQuill reopened = (ItemBookAndQuill) book.clone();
        for (int page = 0; page < 50; page++) {
            assertEquals(expected.get(page), reopened.getPageText(page));
        }
    }

    @Test
    void insertionAtFortyNinePagesRetainsLastExistingText() {
        ItemBookAndQuill book = new ItemBookAndQuill();
        for (int page = 0; page < 49; page++) {
            book.setPageText(page, "Existing page " + page);
        }

        assertTrue(book.insertPage(0, "New first page"));

        ItemBookAndQuill reopened = (ItemBookAndQuill) book.clone();
        assertEquals(50, reopened.getPages().size());
        assertEquals("New first page", reopened.getPageText(0));
        assertEquals("Existing page 48", reopened.getPageText(49));
    }

    @Test
    void ordinaryAddEditInsertDeleteAndSwapPersist() {
        ItemBookAndQuill book = new ItemBookAndQuill();
        assertTrue(book.addPage(0));
        assertTrue(book.setPageText(0, "First"));
        assertTrue(book.insertPage(1, "Second"));
        assertTrue(book.insertPage(1, "Middle"));
        assertTrue(book.deletePage(1));
        assertTrue(book.swapPages(0, 1));

        ItemBookAndQuill reopened = (ItemBookAndQuill) book.clone();
        assertEquals(2, reopened.getPages().size());
        assertEquals("Second", reopened.getPageText(0));
        assertEquals("First", reopened.getPageText(1));
        assertFalse(reopened.swapPages(0, 2));
        assertEquals(2, reopened.getPages().size());
    }

    @Test
    void ordinaryEditAndDeleteRemainAvailableAtCapacity() {
        ItemBookAndQuill book = new ItemBookAndQuill();
        assertTrue(book.addPage(49));
        assertTrue(book.setPageText(49, "Last page"));
        assertTrue(book.addPage(49));
        assertEquals(50, book.getPages().size());
        assertTrue(book.deletePage(0));
        assertTrue(book.insertPage(0, "Replacement first page"));

        ItemBookAndQuill reopened = (ItemBookAndQuill) book.clone();
        assertEquals(50, reopened.getPages().size());
        assertEquals("Replacement first page", reopened.getPageText(0));
        assertEquals("Last page", reopened.getPageText(49));
    }
}
