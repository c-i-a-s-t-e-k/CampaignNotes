#!/bin/bash
# neo4j is already started by system md
# sudo neo4j start

ollama run qwen3 &

#start qdrant
sudo docker run -p 6333:6333 -p 6334:6334     -v "/home/ciastek/Documents/moje_projekty/CampainNotes/qdrant_storage:/qdrant/storage:z"     qdrant/qdrant
