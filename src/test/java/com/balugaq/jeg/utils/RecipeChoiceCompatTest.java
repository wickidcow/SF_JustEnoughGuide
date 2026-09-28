package com.balugaq.jeg.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RecipeChoiceCompatTest {

    @Test
    void createsExactChoiceOnCompatibilityApi() {
        ItemStack stone = new ItemStack(Material.STONE);
        ItemStack dirt = new ItemStack(Material.DIRT);

        var choice = RecipeChoiceCompat.exact(stone, dirt);

        assertEquals(2, choice.getChoices().size());
        assertEquals(Material.STONE, choice.getChoices().get(0).getType());
        assertEquals(Material.DIRT, choice.getChoices().get(1).getType());
    }
}
