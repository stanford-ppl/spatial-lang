#!/bin/bash
cd spatial/core/resources
find . -not -path '*/\.*' -type f > files_list
find . -not -path '*/\.*' -type l >> files_list
