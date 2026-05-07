---

# The Complete 1.20+ Sign API Documentation (Bukkit, Spigot, Paper)

## 1. Introduction to the 1.20 Sign Revolution

Minecraft 1.20 introduced a massive overhaul to how signs function, breaking years of established server-side mechanics. Signs are now double-sided, meaning they feature distinct "Front" and "Back" faces, both capable of holding entirely separate text, dye colors, and glowing states. Furthermore, signs can now be edited after they are placed and can be locked with a honeycomb (waxed) to prevent future modifications.

To accommodate this in the server API, Bukkit/Spigot deprecated almost all text-modifying methods directly on the `Sign` block state (e.g., `Sign#setLine()`) because setting a line on a sign is now ambiguous. Instead, developers must now interact with the `org.bukkit.block.sign.SignSide` interface. This document covers everything you need to know about capturing, modifying, faking, and listening to signs in a modern 1.20+ environment.

---

## 2. Accessing the Sign Block State

Before you can interact with a sign's text or properties, you must first obtain the block state and cast it to `org.bukkit.block.Sign`. This is a standard procedure in the Bukkit API, typically done within an event or by targeting a specific location in the world. Once you have the block, you check if its state is an instance of a Sign. It is important to interact with the block's state rather than the block itself, as the state holds the specific NBT data of the block (like its text). Remember to call `update()` on the state once you are finished making modifications so that the server saves the new data and pushes the changes to the client.

```java
Block block = location.getBlock();
if (block.getState() instanceof org.bukkit.block.Sign sign) {
    // You now have access to the 1.20+ Sign API
    // Make your changes...
    sign.update();
}
```

---

## 3. The `SignSide` Interface: Front and Back

Because of the two distinct sides, the API exposes an Enum named `org.bukkit.block.sign.Side` with two values: `FRONT` and `BACK`. To do almost anything visual to a sign, you must fetch a specific `SignSide` object.

### Getting a Specific Side

You can retrieve a specific side using `sign.getSide(Side)`. This will return a `SignSide` instance which you can then manipulate.

```java
SignSide frontSide = sign.getSide(Side.FRONT);
SignSide backSide = sign.getSide(Side.BACK);
```

### Dynamic Side Retrieval (Targeting what the player sees)

If you are inside an interaction event (such as a player right-clicking a sign), you likely need to know which side the player actually clicked. Spigot provides a highly convenient method called `getTargetSide(Player)`. This performs the math behind the scenes, using the player's line of sight and the block's rotation, to return the exact `SignSide` the player was looking at.

```java
SignSide clickedSide = sign.getTargetSide(player);
```

---

## 4. Reading and Writing Text (Lines)

Once you have obtained your desired `SignSide`, you can manipulate the text. Minecraft signs hold exactly 4 lines (indexed `0` through `3`). Passing an index outside this bound will throw an `IndexOutOfBoundsException`.

### The Bukkit/Spigot Approach (Strings)

Standard Spigot allows you to manipulate signs using standard Java Strings. This allows for legacy color codes (the `§` or `&` symbols if translated) but does not natively support modern Hex components natively without translation.

```java
// Reading
String firstLine = frontSide.getLine(0);
String[] allLines = frontSide.getLines();

// Writing
frontSide.setLine(0, "§bWelcome to");
frontSide.setLine(1, "§aThe Server!");
```

### The PaperMC Approach (Adventure Components)

If you are utilizing Paper (or Purpur), standard string methods are generally discouraged in favor of the Adventure Component API (`net.kyori.adventure.text.Component`). This allows you to apply complex formatting, MiniMessage styling, and RGB gradients directly to the sign lines securely and elegantly.

```java
// Reading components
Component firstLineComp = frontSide.line(0);
List<Component> allComponents = frontSide.lines();

// Writing components natively
frontSide.line(0, Component.text("Welcome").color(NamedTextColor.AQUA));
frontSide.line(1, MiniMessage.miniMessage().deserialize("<gradient:red:blue>Cool Server!</gradient>"));
```

---

## 5. Styling Signs: Colors and Glowing Ink

Prior to 1.20, dye colors and glowing properties were applied to the whole sign. Now, because a player can apply a glow ink sac to the front and regular black dye to the back, these properties are applied strictly to the `SignSide`.

You can check and manipulate both the glowing status (boolean) and the dye color (`org.bukkit.DyeColor`). When modifying these values, remember that they apply universally to all 4 lines on that specific side.

```java
// Make the back side glow and turn the text yellow
backSide.setGlowingText(true);
backSide.setColor(DyeColor.YELLOW);

// Check if a side is glowing
boolean isFrontGlowing = frontSide.isGlowingText();
```

---

## 6. Locking Signs (Waxing) and Editor States

A major feature of 1.20 is the ability to lock signs to prevent further edits. In the game, this is done using a Honeycomb. In the API, this is known as "Waxing". These properties apply to the `Sign` as a whole, not individual sides. If a sign is waxed, nobody can open the editor for it.

```java
// Check if a sign is locked
boolean locked = sign.isWaxed();

// Lock the sign programmatically
sign.setWaxed(true);
```

Additionally, because signs can now be edited dynamically, you may want to know if a player currently has the sign GUI open. The API provides `sign.getAllowedEditor()` which returns the `Player` currently holding the lock on the sign's editing interface. If this returns null, the sign is not currently being edited.

---

## 7. Listening to Sign Interactions and Edits

To create custom sign mechanics (such as clickable command signs or validating what players type), you must rely on standard Bukkit events.

### Intercepting Clicks (`PlayerInteractEvent`)

To create clickable signs, you check for `Action.RIGHT_CLICK_BLOCK`, verify the block is a sign, and then read the side the player clicked.

```java
@EventHandler
public void onSignClick(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

    Block clicked = event.getClickedBlock();
    if (clicked != null && clicked.getState() instanceof Sign sign) {
        Player player = event.getPlayer();
        SignSide side = sign.getTargetSide(player);

        // Example: If line 1 says "[Heal]", heal the player
        if (side.getLine(0).equalsIgnoreCase("[Heal]")) {
            player.setHealth(20.0);
            player.sendMessage("You have been healed!");
            event.setCancelled(true); // Prevent the editor from popping up if the sign is unwaxed
        }
    }
}
```

### Validating Typed Text (`SignChangeEvent`)

When a player clicks "Done" on the sign GUI, the `SignChangeEvent` is fired. In 1.20, this event was updated so you can determine exactly which side was edited using `event.getSide()`. You can modify the lines during this event before they are saved to the server, or cancel the event to reject the edit.

```java
@EventHandler
public void onSignEdit(SignChangeEvent event) {
    Player player = event.getPlayer();
    Side editedSide = event.getSide(); // Returns Side.FRONT or Side.BACK

    // Read the text the player is trying to submit
    String topStr = event.getLine(0);

    if (topStr != null && topStr.equalsIgnoreCase("[Admin]")) {
        if (!player.hasPermission("signs.admin")) {
            player.sendMessage("You cannot create admin signs!");
            event.setCancelled(true);
            return;
        }
        // Change the text before it saves
        event.setLine(0, "§c[Admin]");
    }
}
```

---

## 8. Forcing the Editor Open

The 1.20+ API gives developers the ability to programmatically force a player's client to open the sign-editing GUI. Because signs are now double-sided, the old `player.openSign(Sign)` method has been deprecated. The new standard requires you to specify the `Sign` block state and the `Side` you wish to point the camera at.

```java
// Forces the player to look at and edit the back of the target sign
player.openSign(sign, Side.BACK);
```

_Note: For `openSign` to work seamlessly without glitches, the sign must actually exist at a location within the player's render distance, and the sign must NOT be waxed. If you attempt to open a waxed sign, the client will usually reject the packet._

---

## 9. Virtual Signs and Using Signs as Input GUIs

A popular technique for taking raw text input from players is presenting them with a "Virtual Sign". This means creating the illusion of a sign via packets without actually modifying the physical world.

### Modern Virtual Sign Implementation (1.20+)

The legacy `player.sendSignChange()` is heavily deprecated in modern forks like Purpur and Paper. The correct and modern approach relies on generating a virtual `BlockData` state, sending a block update, and then instructing the player to open that sign. Because the sign only exists on the client, standard server-side `SignChangeEvent` interactions will often fail to fire natively, requiring packet interception.

**Step 1: Send the Virtual Sign Block**
You must pick a location (typically exactly where the player is standing or high up in the sky).

```java
Location loc = player.getLocation();
// Create virtual block data
BlockData virtualSign = Material.OAK_SIGN.createBlockData();

// Send a block update packet to the player so their client thinks a sign exists here
player.sendBlockChange(loc, virtualSign);
```

**Step 2: Force Open the Sign**
You cannot cast a virtual block to a Bukkit `Sign` state because it doesn't exist in the real world. Therefore, some forks provide specific packet-based overrides, but the most universally compatible way is to update the block physically for a tick, or rely on ProtocolLib. If you strictly use Paper's API:

```java
// Faking a sign and prompting input using Paper's packet API
player.sendBlockUpdate(loc, virtualSign);
// You then send the packet to open the sign editor at this coordinate.
```

**Step 3: Catching the Input via Packets**
Because the block doesn't physically exist, Bukkit's `SignChangeEvent` will immediately cancel or not fire at all when the client says "I edited the sign at X, Y, Z" and the server checks that block and finds `Material.AIR`.
To capture the user's text input, you must intercept the incoming packet. The most reliable way is using **ProtocolLib**:

```java
ProtocolLibrary.getProtocolManager().addPacketListener(
    new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.UPDATE_SIGN) {
        @Override
        public void onPacketReceiving(PacketEvent event) {
            Player p = event.getPlayer();
            // Get the block position the client is trying to update
            BlockPosition pos = event.getPacket().getBlockPositionModifier().read(0);

            // Check if this matches your virtual sign's coordinates
            if (isVirtualSignLocation(p, pos)) {
                // Read the lines the player wrote
                String[] lines = event.getPacket().getStringArrays().read(0);

                // Do something with the input!
                Bukkit.getScheduler().runTask(plugin, () -> {
                   p.sendMessage("You typed: " + lines[0]);
                   // Clean up the fake sign by sending air back to the client
                   p.sendBlockChange(new Location(p.getWorld(), pos.getX(), pos.getY(), pos.Z()), Material.AIR.createBlockData());
                });

                // Cancel the packet so the server doesn't throw a "Block mismatch" warning
                event.setCancelled(true);
            }
        }
    }
);
```

---

## 10. The 1.21.x Virtual Sign Input Paradigm

As of 1.20.5 and throughout the **1.21.x** updates, taking user input via a "Virtual Sign" (a GUI without placing an actual block) underwent severe architectural changes.

Minecraft's transition to strictly double-sided signs introduced `isFrontText` boolean flags to all client-server communication. Furthermore, Paper transitioned completely to Mojang mappings, meaning legacy NMS reflection will crash on modern forks. To handle virtual signs safely in 1.21.x, you have three distinct approaches: The **Paper Native Approach**, the **PacketEvents Approach**, and the **ProtocolLib Approach**.

---

### Method A: The Paper Native Approach (Highly Recommended)

If your server runs **Paper 1.21.x+** (or Purpur/Pufferfish), you no longer need to mess with packets at all! Paper introduced a dedicated, native API specifically for capturing virtual sign input.

**Step 1: Opening the Virtual Sign**
Paper added `Player#openVirtualSign()`, which sends the sign GUI packet to the client using a conceptual position, bypassing the need to physically send fake block updates.

```java
// Open a virtual sign interface on the player's current location looking at the FRONT side
player.openVirtualSign(player.getLocation(), Side.FRONT);
```

**Step 2: Listening to the Input**
Because the sign doesn't actually exist in the world, the standard Bukkit `SignChangeEvent` will aggressively cancel the interaction, assuming the player is attempting an exploit. To intercept this gracefully, Paper added the `UncheckedSignChangeEvent`.

```java
@EventHandler
public void onVirtualSignInput(UncheckedSignChangeEvent event) {
    Player player = event.getPlayer();

    // Read the text the player submitted
    String topStr = event.getLine(0);
    player.sendMessage("You typed: " + topStr);

    // Virtual signs usually don't need further logic, so cancel it to prevent backend warnings
    event.setCancelled(true);
}
```

_Note: This approach is completely mapped, completely native, and heavily optimized, making it the absolute best practice for 1.21+ Paper servers._

---

### Method B: The PacketEvents Approach (The Modern Multi-Platform Standard)

If you are developing a plugin that must support multiple server platforms (e.g., Spigot, Folia, Fabric) or you are worried about mapping changes, **PacketEvents** is the modern gold standard. It abstracts all Mojang mapping and NMS changes internally, guaranteeing 1.21 compatibility.

**Step 1: Sending the Fake Block and Opening the Editor**
You still need to send the client the illusion of a block so the client doesn't reject the GUI request.

```java
Location loc = player.getLocation();
Vector3i pos = new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

// 1. Send the fake block change to the client using PacketEvents Wrappers
WrapperPlayServerBlockChange fakeBlock = new WrapperPlayServerBlockChange(pos,
    SpigotConversionUtil.fromBukkitBlockData(Material.OAK_SIGN.createBlockData()));
PacketEvents.getAPI().getPlayerManager().sendPacket(player, fakeBlock);

// 2. Open the GUI (1.20+ requires specifying if it is the front or back)
WrapperPlayServerOpenSignEditor openSign = new WrapperPlayServerOpenSignEditor(pos, true); // true = FRONT side
PacketEvents.getAPI().getPlayerManager().sendPacket(player, openSign);
```

**Step 2: Listening for the Client Response**
You intercept the `UPDATE_SIGN` packet (which fires when the player clicks "Done"). PacketEvents wrappers provide direct methods to check if it's the front text and read the lines without relying on unmapped indexes.

```java
PacketEvents.getAPI().getEventManager().registerListener(
    new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() == PacketType.Play.Client.UPDATE_SIGN) {
                Player player = (Player) event.getPlayer();
                WrapperPlayClientUpdateSign wrapper = new WrapperPlayClientUpdateSign(event);

                // Get the coordinates the client thinks they edited
                Vector3i pos = wrapper.getBlockPosition();

                // Check if they are editing the FRONT or BACK (1.20+ specific property)
                boolean isFront = wrapper.isFrontText();

                // Retrieve the submitted lines natively
                String[] lines = wrapper.getTextArray();

                player.sendMessage("Virtual input received: " + lines[0]);

                // Cleanup: Send an air block back to remove the ghost sign
                WrapperPlayServerBlockChange removeBlock = new WrapperPlayServerBlockChange(pos, 0); // 0 = Air
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, removeBlock);

                event.setCancelled(true); // Suppress the packet from reaching the server
            }
        }
    }
);
```

---

### Method C: The ProtocolLib Approach (1.21.x Updated)

If you already depend on ProtocolLib, you must account for the 1.21.x updates. Because of the Paper mapping removal, legacy reflection arrays will fail. Internally, the packet the client sends is now defined by Mojang as `ServerboundSignUpdatePacket`. The ProtocolLib container structures have updated their index maps to account for the double-sided sign booleans.

**Step 1: Spoof the sign**
Using standard Bukkit methods (or ProtocolLib block change packets), send the fake block, then prompt the Bukkit API to open the sign.

```java
Location loc = player.getLocation();
player.sendBlockChange(loc, Material.OAK_SIGN.createBlockData());
// It is recommended to delay this by 1 tick so the block change registers on the client
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    player.openSign(loc, Side.FRONT);
}, 1L);
```

**Step 2: Intercept via ProtocolLib Structure Modifiers**
When capturing the `UPDATE_SIGN` packet in 1.21.x, you must carefully read the modifiers. The structure contains:

- `BlockPosition` at index 0.
- `Boolean` (`isFrontText`) at index 0.
- `String[]` at index 0.

```java
ProtocolLibrary.getProtocolManager().addPacketListener(
    new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.UPDATE_SIGN) {
        @Override
        public void onPacketReceiving(PacketEvent event) {
            Player player = event.getPlayer();
            PacketContainer packet = event.getPacket();

            // Read exactly where the client thought the sign was
            BlockPosition pos = packet.getBlockPositionModifier().read(0);

            // 1.20+ addition: Did the player edit the front or back?
            boolean isFrontText = packet.getBooleans().read(0);

            // The 4 lines of text
            String[] lines = packet.getStringArrays().read(0);

            // Your logic...
            String input = lines[0];

            // Back on the main thread, clear the fake block
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("Received input: " + input);
                player.sendBlockChange(new Location(player.getWorld(), pos.getX(), pos.getY(), pos.getZ()), Material.AIR.createBlockData());
            });

            // Cancel packet to prevent the server console from throwing "Player edited an invalid sign" warnings
            event.setCancelled(true);
        }
    }
);
```

### Summary of 1.21 Requirements

1. **Always account for `isFrontText`** if using packet libraries; signs are now treated as two separate blocks of data merged into one packet.
2. **Beware of Mappings:** If manually editing the packet structure without a wrapper like PacketEvents, 1.21 Paper builds will crash if you use Spigot NMS reflection. Always target `ServerboundSignUpdatePacket`.
3. **Use the Native API if possible:** Unless you explicitly support Spigot, `Player#openVirtualSign` combined with `UncheckedSignChangeEvent` removes the need for 95% of legacy boiler-plate code.
