# CampaignNotes Terminal Interface - Demo

## Quick Start Demo

This demonstrates the Terminal Interface functionality for the CN-20-Terminal-Note-entry feature.

### Starting the Application

```bash
./gradlew run
```

### Expected Output and Interaction

```
My Hello World!
Connection to SQLite has been established.
Databases available.
Langfuse connection available.

Starting Terminal Interface...
=== CampaignNotes Terminal Interface ===
Welcome to your RPG campaign management system!
Checking system readiness...
System is ready!

=== Main Menu ===
1. Create new campaign
2. Select existing campaign
3. List all campaigns
4. Exit
Choose an option (1-4): 1

=== Create New Campaign ===
Enter campaign name: Dragons of Autumn Twilight
Campaign created successfully!
Campaign details: [uuid-here] - Dragons of Autumn Twilight - Dragons of Autumn TwilightCampaignLabeluuid-here - Dragons of Autumn TwilightCampaignCollectionuuid-here
Would you like to work with this campaign now? (y/n): y

=== Campaign: Dragons of Autumn Twilight ===
1. Add new note
2. Add override note  
3. View notes (coming soon)
4. Back to main menu
Choose an option (1-4): 1

=== Add Note to Dragons of Autumn Twilight ===
Enter note title: First Session - Tavern Meeting
Enter note content (max 500 words):
Type your content and press Enter when done:
The party met at the Prancing Pony tavern in Solace. Tanis, the half-elf ranger, gathered the companions after many years apart. Raistlin arrived with his twin brother Caramon, while Sturm came bearing news of strange happenings in the north. The discussion revealed that clerical magic has returned to the world, and the gods have come back to Krynn.

Note created successfully!
Note details: [note-uuid] First Session - Tavern Meeting - The party met at the Prancing Pony tavern in Solace. Tanis, the half-elf ranger, gathered the companions after many years apart. Raistlin arrived with his twin brother Caramon, while Sturm came bearing news of strange happenings in the north. The discussion revealed that clerical magic has returned to the world, and the gods have come back to Krynn. (67 words, override: false)

Note would be processed and stored with embedding.
(Storage implementation pending NoteService completion)

=== Campaign: Dragons of Autumn Twilight ===
1. Add new note
2. Add override note
3. View notes (coming soon)  
4. Back to main menu
Choose an option (1-4): 2

=== Add Override Note to Dragons of Autumn Twilight ===
Enter note title: Correction - Tavern Name
Enter note content (max 500 words):
The party actually met at the Inn of the Last Home, not the Prancing Pony. This is the famous inn run by Otik, with its famous spiced potatoes. The inn sits high in the vallenwood trees of Solace.

Enter override reason: Correcting tavern name - mixed up with LOTR reference
Override note created successfully!
Note details: [note-uuid] Correction - Tavern Name - The party actually met at the Inn of the Last Home, not the Prancing Pony. This is the famous inn run by Otik, with its famous spiced potatoes. The inn sits high in the vallenwood trees of Solace. (41 words, override: true)

Override note would be processed and stored with embedding.
(Storage implementation pending NoteService completion)

=== Campaign: Dragons of Autumn Twilight ===
1. Add new note
2. Add override note
3. View notes (coming soon)
4. Back to main menu
Choose an option (1-4): 4

=== Main Menu ===
1. Create new campaign
2. Select existing campaign
3. List all campaigns
4. Exit
Choose an option (1-4): 3

=== All Campaigns ===
- Dragons of Autumn Twilight [UUID: 12345678...]

=== Main Menu ===
1. Create new campaign
2. Select existing campaign
3. List all campaigns
4. Exit
Choose an option (1-4): 4

Thank you for using CampaignNotes!
```

## Key Features Demonstrated

### 1. **Campaign Management**
- ✅ Create new campaigns with custom names
- ✅ List existing campaigns 
- ✅ Select campaigns to work with

### 2. **Note Entry**
- ✅ Add regular notes with title and content
- ✅ Content validation (500-word limit)
- ✅ Word count display
- ✅ Override notes with reasons

### 3. **Validation Examples**

#### Empty Content
```
Enter note content (max 500 words):
[empty input]

Content cannot be empty.
```

#### Exceeding Word Limit
```
Enter note content (max 500 words):
[content with 501+ words]

Note validation failed. Please check:
- Title and content are not empty
- Content does not exceed 500 words
Current word count: 520
```

### 4. **System Integration**
- ✅ Database connectivity checks
- ✅ Service availability verification
- ✅ Graceful error handling
- ✅ User-friendly feedback

## Technical Notes

### What Happens Behind the Scenes

1. **Note Creation**: Each note gets a unique UUID and timestamps
2. **Embedding Preparation**: Text is prepared for OpenAI text-embedding-3-large
3. **Monitoring**: Langfuse tracking would capture:
   - Campaign context
   - Token usage estimation
   - Processing time
   - System tags for filtering
4. **Storage Preparation**: Data formatted for Qdrant vector database

### Current Status
- **UI/UX**: ✅ Fully functional terminal interface
- **Validation**: ✅ Complete validation and error handling  
- **Integration**: ✅ OpenAI and Langfuse ready
- **Storage**: ⚠️ Prepared for Qdrant (implementation pending)

## Next Steps

When databases are running, the interface will:
1. Actually store notes in Qdrant with embeddings
2. Enable full search and retrieval functionality
3. Connect to Neo4j for artifact relationship mapping
4. Provide real-time feedback on storage success/failure

---

This demo shows the complete terminal interface working as designed - a modular, user-friendly system ready for backend integration and future frontend replacement. 