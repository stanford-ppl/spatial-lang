#include <unistd.h>
#include <stdio.h>

int main()
{
    long sz = sysconf(_SC_PAGESIZE);
    printf("pagesize = %ld", sz);
    return 0;
}