#!/usr/bin/env python

### Plot results
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import matplotlib.ticker as ticker
import os, sys
import math

benchmarks = ['BlackScholes'] #['OutProd', 'DotProduct'] #['BlackScholes', 'DotProduct', 'GDA', 'Kmeans', 'GEMM', 'OutProd', 'TPCHQ6']
args = {"DotProduct": [4512, 6, 12, True],
                "OutProd":	[192, 4, 96, True],
                "TPCHQ6": [18720, 3, 48, True],
                "BlackScholes": [14496, 1, 7, True],
                "GEMM": [50, 96, 864, 2, 2, 12, True],
                "Kmeans": [400, 1, 8, 3, 3, 1, True],
                "GDA": [960, 1, 16, 16, 2, 1, True, True]}
labels = ['A', 'B', 'C', 'D', 'E', 'F', 'G']
plotInvalid = True

numCols = 1
numRows = len(benchmarks)/numCols
fig, axes = plt.subplots(len(benchmarks)/numCols, numCols*3, sharex='col', sharey='row')
fig.subplots_adjust(wspace=0.12)
fig.subplots_adjust(hspace=0.1)
font_size = 8
marker_iv = '.'
marker_v = '.'
marker_pd = '*'
marker_comp = '*'
color_iv = '#A9A9A9'
color_spd = '#006600'
color_mpd = '#33CC33'
color_seq = '#FFA500'
color_met = '#1E90FF'
color_comp = 'r'
dotSize = 20
compSize = 50
ano_loc = (0.9, 0.85)

for (idx, bm) in enumerate(benchmarks):
    f = open('./results/' + bm + '_data.csv')
    s = f.read()
    f.close()
    lines = s.split('\n')
    superHeader = lines[0].split(',')
    header = lines[1].split(',')

    ALMS = 0

    for i in range(0, len(superHeader)):
        if 'OUTPUTS' in superHeader[i]: ALMS = i

    DSPS = ALMS + 1
    BRAM = ALMS + 2
    CYCL = ALMS + 3
    VLID = ALMS + 4

    alms = []
    dsps = []
    bram = []
    cycl = []
    vlid = []
    line = []
    pare = []

    for i in range(2,len(lines)-1):
        ln = lines[i].split(',')
        alms.append( 100 * float(ln[ALMS].rstrip()) / 262400 )
        dsps.append( 100 * float(ln[DSPS].rstrip()) / 1963 )
        bram.append( 100 * float(ln[BRAM].rstrip()) / 2567 )
        cyc = float(ln[CYCL].rstrip())
        if cyc > 0: cycl.append( math.log10(cyc) )
        else: cycl.append(-1)
        vlid.append('true' in ln[VLID])
        line.append(ln)

    N = len(vlid)

    almsG = [[],[],[],[],[]]
    dspsG = [[],[],[],[],[]]
    bramG = [[],[],[],[],[]]
    cyclG = [[],[],[],[],[]]
    compP = []

    for i in range(0,N):
        pareto = vlid[i]
        if i == 0: j = 1
        else: j = 0
        while pareto and j < N:
            pareto = (not vlid[j]) or alms[i] < alms[j] or cycl[i] < cycl[j] or (alms[i] <= alms[j] and cycl[i] < cycl[j]) or (alms[i] < alms[j] and cycl[i] <= cycl[j])
            j = j + 1
            if (j == i): j = j + 1

        pare.append(pareto)
        #if pareto: print line[i]

        if vlid[i]:
            if pareto and meta[i]: g = 0
            elif pareto: g = 1
            elif meta[i]: g = 2
            else: g = 3
        else: g = 4

        if alms[i] > 0 and cycl[i] > 0:
            almsG[g].append(alms[i])
            bramG[g].append(bram[i])
            dspsG[g].append(dsps[i])
            cyclG[g].append(cycl[i])

        match = True
        #print bm
        for (argIdx, arg) in enumerate(args[bm]):
            currArg = line[i][argIdx].rstrip()
            if type(arg) is int:
                currArg = int(currArg)
            if type(arg) is bool:
                currArg = 'true' in currArg
            if (currArg!=arg): match=False
            #print str(argIdx) + " " + str(arg) + " " + str(currArg) + " " + str(currArg==arg)
        if match:
            if len(compP)!=0: print 'Error! already find the comp point!'; exit()
            #if not vlid[i]: print 'Error! comp point found is not vaid!'; exit()
            compP = [alms[i], dsps[i], bram[i], cycl[i]]
            #print str(line[i]) + str(compP)+ " match!!!"
    #if len(compP)==0: compP = [3,2,3,5]

    if len(compP)==0: compP = [0, 0, 0, 0] #print 'Error! did not find comp!'; exit()

    #### Start plotting
    rowIdx = idx%numRows
    colIdx = (idx-rowIdx)/numRows
    ax1 = axes[rowIdx][colIdx*3+0]
    ax2 = axes[rowIdx][colIdx*3+1]
    ax3 = axes[rowIdx][colIdx*3+2]
    ####### ALMs
    #ax.set_title("{0} Performance/Area Tradeoff (ALMs)".format(sys.argv[1]))
    # Add some axis labels.
    if (rowIdx==(numRows-1)):
        ax1.set_xlabel("ALM", fontsize=font_size)
    ax1.set_ylabel(bm, fontsize=font_size)
    ax1.tick_params(axis='both', which='major', labelsize=font_size)
    ax1.get_yaxis().set_major_locator(ticker.MaxNLocator(integer=True))

    if plotInvalid:
        iv = ax1.scatter(almsG[4], cyclG[4], c = color_iv, s = dotSize, marker = marker_iv, edgecolors='none', label = 'Invalid')

    s =	ax1.scatter(almsG[3], cyclG[3], c = color_met, s = dotSize, marker = marker_v,
            edgecolors='none', label = 'Sequential')
    m =	ax1.scatter(almsG[2], cyclG[2], c = color_seq, s = dotSize, marker = marker_v,
            edgecolors='none', label = 'CoarsePipe', alpha = 0.3)
    sp = ax1.scatter(almsG[1], cyclG[1], c = color_spd, s = dotSize, marker = marker_pd, edgecolors=color_spd, label = 'Sequential Pareto')
    mp = ax1.scatter(almsG[0], cyclG[0], c = color_mpd, s = dotSize, marker = marker_pd, edgecolors=color_mpd, label = 'CoarsePipe Pareto')
    comp = ax1.scatter(compP[0], compP[3], c = color_comp, s=compSize, marker =
            marker_comp, label = 'Compared Design')
    #print bm + " mp" + str(almsG[0]) + str(cyclG[0])
    iv.set_rasterized(True)
    s.set_rasterized(True)
    m.set_rasterized(True)
    sp.set_rasterized(True)
    mp.set_rasterized(True)
    comp.set_rasterized(True)

    ax1.grid()
    ax1.set_xlim([-1,120])
    ax1.annotate(chr(65+idx*3+0), ano_loc, fontsize=font_size, xycoords='axes fraction',	ha='center',
            va='center', weight='bold')

    ######### DSPs
    #ax.set_title("{0} Performance/Area Tradeoff (DSPs)".format(sys.argv[1]))
    if (rowIdx==(numRows-1)):
        ax2.set_xlabel("DSP", fontsize=font_size)
    ax2.tick_params(axis='both', which='major', labelsize=font_size)
    ax2.get_yaxis().set_major_locator(ticker.MaxNLocator(integer=True))

    if plotInvalid:
        iv = ax2.scatter(dspsG[4], cyclG[4], c = color_iv, s = dotSize, marker = marker_iv, edgecolors='none', label = 'Invalid')

    s =	ax2.scatter(dspsG[3], cyclG[3], c = color_met, s = dotSize, marker = marker_v,
            edgecolors='none',label = 'Sequential')
    m =	ax2.scatter(dspsG[2], cyclG[2], c = color_seq, s = dotSize, marker = marker_v,
            edgecolors='none',label = 'CoarsePipe', alpha = 0.3)
    sp = ax2.scatter(dspsG[1], cyclG[1], c = color_spd, s = dotSize, marker = marker_pd, edgecolors='none',label = 'Sequential Pareto')
    mp = ax2.scatter(dspsG[0], cyclG[0], c = color_mpd, s = dotSize, marker = marker_pd,edgecolors='none',label = 'CoarsePipe Pareto')
    comp = ax2.scatter(compP[1], compP[3], c = color_comp, s=compSize, marker =
            marker_comp, label = 'Compared Design')

    #plt.legend([m, mp], ['Metapipeline', 'Metapipeline + Pareto'])
    ax2.grid()
    ax2.set_xlim([-1,120])
    ax2.annotate(chr(65+idx*3+1), ano_loc, fontsize=font_size, xycoords='axes fraction',	ha='center',
            va='center', weight='bold')
    iv.set_rasterized(True)
    s.set_rasterized(True)
    m.set_rasterized(True)
    sp.set_rasterized(True)
    mp.set_rasterized(True)
    comp.set_rasterized(True)

    ######## BRAM
    #ax.set_title("{0} Performance/Area Tradeoff (BRAMs)".format(sys.argv[1]))
    if (rowIdx==(numRows-1)):
        ax3.set_xlabel("BRAM", fontsize=font_size)
    ax3.tick_params(axis='both', which='major', labelsize=font_size)
    ax3.get_yaxis().set_major_locator(ticker.MaxNLocator(integer=True))

    if plotInvalid:
        iv = ax3.scatter(bramG[4], cyclG[4], c = color_iv, s = dotSize, marker = marker_iv, edgecolors='none', label = 'Invalid')

    s =	ax3.scatter(bramG[3], cyclG[3], c = color_met, s = dotSize, marker =
            marker_v,edgecolors='none',label = 'Sequential')
    m =	ax3.scatter(bramG[2], cyclG[2], c = color_seq, s = dotSize, marker =
            marker_v,edgecolors='none', label = 'CoarsePipe', alpha = 0.3)
    sp = ax3.scatter(bramG[1], cyclG[1], c = color_spd, s = dotSize, marker = marker_pd,edgecolors='none', label = 'Sequential Pareto')
    mp = ax3.scatter(bramG[0], cyclG[0], c = color_mpd, s = dotSize, marker =
            marker_pd,edgecolors='none', label = 'CoarsePipe Pareto')
    comp = ax3.scatter(compP[2], compP[3], c = color_comp, s=compSize, marker =
            marker_comp, label = 'Compared Design')

    ax3.grid()
    ax3.set_xlim([-1,120])
    ax3.tick_params(axis='x', which='major', labelsize=font_size-1)
    ax3.annotate(chr(65+idx*3+2), ano_loc, fontsize=font_size, xycoords='axes fraction',	ha='center',
            va='center', weight='bold')
    iv.set_rasterized(True)
    s.set_rasterized(True)
    m.set_rasterized(True)
    sp.set_rasterized(True)
    mp.set_rasterized(True)
    comp.set_rasterized(True)

    if (rowIdx==0):
        ax3.legend([m, s, iv, mp, sp, comp], ['Designs with CoarsePipe only',
        'Designs with at least one Sequential',
        'Invalid design using more than 100% resource',
            'Pareto points that have CoarsePipe only',
            'Pareto points that have at least one Sequential',
            'Design compared with CPU'],
                bbox_to_anchor=(1.0, 1.6), ncol=2, fontsize=font_size-1)

fig.text(0.5, 0.04, 'Usage (% of maximum)', ha='center')
fig.text(0.04, 0.5, 'Cycles (Log Scale)', va='center', rotation='vertical')
fig.set_size_inches(7,9)
#plt.show()
plt.savefig('tradeoff.png', format='png', dpi=900)


def onclick(event):
    print 'button=%d, x=%d, y=%d, xdata=%f, ydata=%f'%(event.button, event.x, event.y, event.xdata, event.ydata)
    nalms	= event.xdata
    cycles = event.ydata

    minIndex = 0
    minDist = -1
    minDSP = -1
    minBRAM = -1
    for i in range(0,N):
        if vlid[i]:
            dist = abs((alms[i] - nalms)/nalms)*abs((cycl[i] - cycles)/cycles)
            if dist < minDist or minDist < 0:
                minDist = dist
                minIndex = i

    closestALM = alms[minIndex]
    closestCycles = cycl[minIndex]
    minBRAM = bram[minIndex]

    print lines[0]
    print 'Closest:', line[minIndex], pare[minIndex]

    for i in range(0,N):
        if vlid[i] and abs((alms[i] - closestALM)/closestALM) < 0.05 and abs((cycl[i] - closestCycles)/closestCycles) < 0.05 and bram[i] < minBRAM:
            minBRAM = bram[i]
            minIndex = i

    print 'Smallest BRAM nearby: ', line[minIndex], pare[minIndex]
    #

#cid = fig.canvas.mpl_connect('button_press_event', onclick)
