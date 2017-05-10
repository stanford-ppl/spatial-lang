#!bin/python

# Helpful script for multiplying flattened matrices
import numpy as np


M = int(raw_input("(MxP) * (PxN) Multiplication: Dimension M? "))
P = int(raw_input("(MxP) * (PxN) Multiplication: Dimension P? "))
N = int(raw_input("(MxP) * (PxN) Multiplication: Dimension N? "))

A = raw_input("A * B Multiplication: Flattened A? ")
A = A.split(" ")
A = [ float(x) for x in A ]
B = raw_input("A * B Multiplication: Flattened B? ")
B = B.split(" ")
B = [ float(x) for x in B ]

A = np.reshape(A, (M,P))
B = np.reshape(B, (P,N))

C = np.dot(A,B)

print C