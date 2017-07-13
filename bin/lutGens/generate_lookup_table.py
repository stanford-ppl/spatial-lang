import math

def get_sigmoid_lut_entries(lo, up, n_samples, n_decimals):
    list = []
    prec = (float(up) - float(lo)) / n_samples
    for i in iter(range(n_samples)):
        list.append(1. / (1. + math.exp(-(i * prec + lo))))
    format_string = "{0:." + str(n_decimals) + "f}"
    re_list = ", ".join([format_string.format(v) for v in list])
    return re_list

def get_tanh_lut_entries(lo, up, n_samples, n_decimals):
    list = []
    prec = (float(up) - float(lo)) / n_samples
    for i in iter(range(n_samples)):
        list.append(math.tanh(i * prec + lo))
    format_string = "{0:." + str(n_decimals) + "f}"
    re_list = ", ".join([format_string.format(v) for v in list])
    return re_list

if __name__ == "__main__":
#    print(get_sigmoid_lut_entries(-32., 32., 128, 6))
    print(get_tanh_lut_entries(-32., 32., 128, 6))
